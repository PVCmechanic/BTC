import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class BencodePeer implements Runnable {

  public final String HANDSHAKE = "BitTorrent protocol";
  public final byte[] RESERVED = new byte[8];
  private Bencode parent;
  private FileManager mgr;
  private InetSocketAddress ip;
  private Socket socket;
  private byte[] infoHash;
  private byte[] id;
  private byte[] pstr;
  private byte[] reserved;
  private byte[] peerId;
  private byte[] peerHash;
  private boolean choking;
  private boolean interested;
  private boolean peerChoking;
  private boolean peerInterested;
  private int outstandingRequests;
  private final Object outstandingRequestsLock;
  private final Queue<Request> peerRequests;
  private final BlockingQueue<Integer> newPieces;
  private byte[] peerBitfield;
  private final Object peerBitfieldLock;
  private int subRatio;
  private long time;
  private int fives;
  private int subPieceLength;
  private int lastSubpiece;
  private int lastSubPieceLength;
  private boolean done;

  private PeerSend peerSend;
  private PeerRecv peerRecv;

  public BencodePeer() {
    choking = true;
    interested = false;
    peerChoking = true;
    peerInterested = false;
    peerRequests = new LinkedList<>();
    newPieces = new LinkedBlockingQueue<>();
    time = 0;
    fives = 0;
    done = false;
    peerBitfieldLock = new Object();
    outstandingRequests = 0;
    outstandingRequestsLock = new Object();
  }

  public BencodePeer(
      InetSocketAddress ip, byte[] infoHash, byte[] id, Bencode parent, FileManager mgr)
      throws IOException {
    this();
    this.ip = ip;
    this.socket = new Socket();
    socket.connect(ip, 5000);
    this.infoHash = infoHash;
    this.id = id;
    this.parent = parent;
    done = mgr.isDone();
    peerBitfield = new byte[mgr.getBitfield().length];
    this.mgr = mgr;
    subRatio = mgr.getSubRatio();
    lastSubpiece = mgr.getLastSubpiece();
    lastSubPieceLength = mgr.getLastSubpieceLength();
    subPieceLength = mgr.SUBPIECELENGTH;
  }

  public BencodePeer(Socket sock, byte[] infoHash, byte[] id, Bencode parent, FileManager mgr) {
    this();
    this.socket = sock;
    this.ip = (InetSocketAddress) sock.getRemoteSocketAddress();
    this.infoHash = infoHash;
    this.id = id;
    this.parent = parent;
    done = mgr.isDone();
    peerBitfield = new byte[mgr.getBitfield().length];
    this.mgr = mgr;
    subRatio = mgr.getSubRatio();
    lastSubpiece = mgr.getLastSubpiece();
    lastSubPieceLength = mgr.getLastSubpieceLength();
    subPieceLength = mgr.SUBPIECELENGTH;
  }

  public void run() {
    try (DataInputStream in = new DataInputStream(socket.getInputStream());
        OutputStream out = socket.getOutputStream()) {

      peerSend = new PeerSend(out, this, Thread.currentThread());
      peerRecv = new PeerRecv(in, this, Thread.currentThread());

      peerSend.handshake(infoHash, id);
      peerSend.bitfield();

      reserved = new byte[8];
      peerHash = new byte[20];
      peerId = new byte[20];
      pstr = peerRecv.receiveHeader(reserved, peerHash, peerId);

      if (!Arrays.equals(peerHash, infoHash)) {
        System.out.println("bad hash");
        throw new IOException();
      }

      Thread sendThread = new Thread(peerSend);
      sendThread.start();

      Thread recvThread = new Thread(peerRecv);
      recvThread.start();

      try {
        Thread.sleep(Long.MAX_VALUE);
      } catch (InterruptedException ignored) {
      }

      sendThread.interrupt();
      recvThread.interrupt();

    } catch (IOException e) {
      System.out.printf("Connection to %s closed\n", ip.getHostString());
    } finally {
      System.out.printf("Remaining HAVE messages (%s)\n", ip.getHostString());

      for (Integer i : newPieces) {
        System.out.println(i);
      }
      parent.decThreads();
      if (!choking) {
        parent.decUnchoked();
      }
    }
  }

  private void interested() {
    if (!interested) {
      peerSend.setInterested();
    }
    interested = true;
  }

  private void peerDisinterested() {
    if (!choking) {
      peerSend.setChoke();
    }
    peerInterested = false;
    choking = false;
  }

  public void newPiece(int index) {
    newPieces.offer(index);
  }

  private static int networkInt(int a, int b, int c, int d) {
    return (d & 0xff) | ((c & 0xff) << 8) | ((b & 0xff) << 16) | ((a & 0xff) << 24);
  }

  private class PeerRecv implements Runnable {
    DataInputStream in;
    BencodePeer parent;
    Thread parentThread;

    public PeerRecv(DataInputStream in, BencodePeer parent, Thread parentThread) {
      this.in = in;
      this.parent = parent;
      this.parentThread = parentThread;
    }

    @Override
    public void run() {
      try {
        int len;
        while (true) {
          if (Thread.interrupted()) {
            break;
          }
          len = in.readInt();
          //          System.out.printf("%s length %d\n", parent.ip.getHostString(), len);
          if (len > 0) {

            int messageId = in.read();
            byte[] data = new byte[len - 1];
            in.readFully(data);
//            System.out.printf("%s received ", parent.ip.getHostString());
            switch (messageId) {
              case 0:
                System.out.printf("%s choking\n", parent.ip.getHostString());
                parent.peerChoking = true;
                break;
              case 1:
                System.out.printf("%s unchoking\n", parent.ip.getHostString());
                parent.peerChoking = false;
                break;
              case 2:
                System.out.printf("%s interested\n", parent.ip.getHostString());
                parent.peerInterested = true;
                break;
              case 3:
                System.out.printf("%s disinterested\n", parent.ip.getHostString());
                parent.peerInterested = false;
                parent.peerDisinterested();
                break;
              case 4:
                {
                  int index = networkInt(data[0], data[1], data[2], data[3]);
                  System.out.printf("%s have %d\n", parent.ip.getHostString(), index);
                  synchronized (parent.peerBitfieldLock) {
                    parent.peerBitfield[index / 8] |= (1 << (7 - index % 8));
                    if (!parent.interested && parent.mgr.peerHasMissing(parent.peerBitfield)) {
                      parent.interested();
                    }
                  }
                  break;
                }
              case 5:
                System.out.printf("%s bitfield ", parent.ip.getHostString());
                System.out.print("data:\t");
                for (byte piece : data) {
                  System.out.print(
                      String.format("%8s", Integer.toBinaryString(piece & 0xff)).replace(' ', '0')
                          + " ");
                }
                System.out.println();
                synchronized (parent.peerBitfieldLock) {
                  parent.peerBitfield = data;
                  if (!parent.interested && parent.mgr.peerHasMissing(parent.peerBitfield)) {
                    parent.interested();
                    parent.interested = true;
                  }
                }
                break;
              case 6:
                {
                  System.out.printf("%s request", parent.ip.getHostString());
                  int index = networkInt(data[0], data[1], data[2], data[3]);
                  int offset = networkInt(data[4], data[5], data[6], data[7]);
                  int length = networkInt(data[8], data[9], data[10], data[11]);
                  if (parent.mgr.hasPiece(index)) {
                    parent.peerRequests.add(new Request(index, offset, length));
                  }
                  break;
                }
              case 7:
                {
//                  System.out.printf("piece from %s\n", ip.getHostString());
                  synchronized (outstandingRequestsLock) {
                    outstandingRequests--;
                  }
                  int index = networkInt(data[0], data[1], data[2], data[3]);
                  int offset = networkInt(data[4], data[5], data[6], data[7]);
                  byte[] subpiece = Arrays.copyOfRange(data, 8, data.length);
                  if (parent.mgr.setPiece(index, offset, subpiece)) {
                    parent.parent.sendHas(index);
                  }
                  break;
                }
              case 8:
                {
                  System.out.printf("%s cancel", parent.ip.getHostString());
                  int index = networkInt(data[0], data[1], data[2], data[3]);
                  int offset = networkInt(data[4], data[5], data[6], data[7]);
                  int length = networkInt(data[8], data[9], data[10], data[11]);
                  parent.peerRequests.remove(new Request(index, offset, length));
                  break;
                }
              default:
                System.out.printf("BAD from %s\n", ip);
            }
          }
        }
      } catch (IOException e) {
        System.out.printf("Recv socket IOException: %s\n", parent.ip.getHostString());
        parentThread.interrupt();
      }
      System.out.printf("recv done %s\n", parent.ip.getHostString());
    }

    private byte[] receiveHeader(byte[] reserved, byte[] peerHash, byte[] peerId)
        throws IOException {
      int pstrlen = in.read();
      if (pstrlen == 0) {
        System.out.printf("%s closed?\n", ip.getHostString());
      }

      byte[] pstr = new byte[pstrlen];
      in.readFully(pstr);
      in.readFully(reserved);
      in.readFully(peerHash);
      in.readFully(peerId);
      if (!Arrays.equals(peerHash, infoHash)) {
        System.out.println("bad hash");
        throw new IOException();
      }
      return pstr;
    }
  }

  private class PeerSend implements Runnable {
    private OutputStream out;
    private BencodePeer parent;
    private Thread parentThread;
    private AtomicBoolean sendInterested;
    private AtomicBoolean sendChoke;

    public PeerSend(OutputStream out, BencodePeer parent, Thread parentThread) {
      this.out = out;
      this.parent = parent;
      this.parentThread = parentThread;
      sendInterested = new AtomicBoolean(false);
      sendChoke = new AtomicBoolean(false);
    }

    @Override
    public void run() {
      try {
        while (true) {
          if (Thread.interrupted()) {
            break;
          }
          while (!parent.newPieces.isEmpty()) {
            have(parent.newPieces.take());
            synchronized (parent.peerBitfieldLock) {
              if (!parent.mgr.peerHasMissing(parent.peerBitfield)) {
                disinterested();
                parent.interested = false;
              }
            }
            parent.done = parent.mgr.isDone();
          }

          if (sendInterested.compareAndExchange(true, false)) {
            interested();
          }

          if (sendChoke.compareAndExchange(true, false)) {
            choke();
          }

          if (!parent.peerRequests.isEmpty() && !parent.choking && parent.peerInterested) {
            Request request = parent.peerRequests.remove();
            piece(request);
          }

          if (!parent.done) {
            if (!parent.peerChoking && parent.interested) {
              synchronized (outstandingRequestsLock) {
                if (outstandingRequests < 5) {
                  outstandingRequests++;
                  int offset;
                  synchronized (parent.peerBitfieldLock) {
                    offset = parent.mgr.getRequest(parent.peerBitfield);
                  }
                  if (offset >= 0) {
                    int index = offset / parent.subRatio;
                    int subIndex = offset % parent.subRatio;
                    request(index, subIndex);
                  } else {
                    disinterested();
                    parent.interested = false;
                  }
                }
              }
            }
          } // IF_DONE
          if (System.currentTimeMillis() - parent.time > 5000) {
            parent.time = System.currentTimeMillis();
            if (parent.parent.getUnchoked() < 4 && parent.peerInterested && parent.choking) {
              parent.choking = false;
              unchoke();
              parent.parent.incUnchoked();
            }
          }
        }
      } catch (IOException e) {
        System.out.printf("Send socket IOException: %s\n", parent.ip.getHostString());
        parentThread.interrupt();
      } catch (InterruptedException e) {
        System.out.printf("Send thread interrupted: %s\n", parent.ip.getHostString());
        parentThread.interrupt();
      }
      System.out.printf("send done %s\n", parent.ip.getHostString());
    }

    private void setInterested() {
      sendInterested.set(true);
    }

    private void setChoke() {
      sendChoke.set(true);
    }

    private void handshake(byte[] infoHash, byte[] id) throws IOException {
      int size =
          1 + parent.HANDSHAKE.length() + parent.RESERVED.length + infoHash.length + id.length;
      ByteBuffer buf = ByteBuffer.allocate(size);
      buf.put((byte) parent.HANDSHAKE.length());
      buf.put(parent.HANDSHAKE.getBytes());
      buf.put(parent.RESERVED);
      buf.put(infoHash);
      buf.put(id);
      out.write(buf.array());
    }

    private void choke() throws IOException {
      System.out.println("Sent CHOKING");
      out.write(new byte[] {0, 0, 0, 1, 0});
    }

    private void unchoke() throws IOException {
      System.out.println("Sent UNCHOKING");
      out.write(new byte[] {0, 0, 0, 1, 1});
    }

    private void interested() throws IOException {
      System.out.println("Sent INTERESTED");
      out.write(new byte[] {0, 0, 0, 1, 2});
    }

    private void disinterested() throws IOException {
      System.out.println("Sent DISINTERESTED");
      out.write(new byte[] {0, 0, 0, 1, 3});
    }

    private void have(int index) throws IOException {
      System.out.printf("Sent HAVE, piece %d, to %s\n", index, ip.getHostString());
      ByteBuffer buffer = ByteBuffer.allocate(9);
      buffer.putInt(5);
      buffer.put((byte) 4);
      buffer.putInt(index);
      out.write(buffer.array());
    }

    private void bitfield() throws IOException {
      byte[] bitfield = parent.mgr.getBitfield();
      int len = bitfield.length + 1;
      ByteBuffer buf = ByteBuffer.allocate(4 + len);
      buf.putInt(len);
      buf.put((byte) 5);
      buf.put(bitfield);
      System.out.printf("Sent BITFIELD, data: %s\n", Arrays.toString(buf.array()));
      out.write(buf.array());
    }

    private void piece(Request request) throws IOException {
      System.out.printf("Sent PIECE, piece %d @ %d\n", request.index, request.offset);
      byte[] data = parent.mgr.readPiece(request.index, request.offset, request.length);
      ByteBuffer buffer = ByteBuffer.allocate(13 + data.length);
      buffer.putInt(9 + data.length);
      buffer.put((byte) 7);
      buffer.putInt(request.index);
      buffer.putInt(request.offset);
      buffer.put(data);
      out.write(buffer.array());
    }

    private void request(int index, int offset) throws IOException {
//      System.out.printf("Sent REQUEST: piece %d @ %d\n", index, offset);
      ByteBuffer buffer = ByteBuffer.allocate(17);
      buffer.putInt(13);
      buffer.put((byte) 6);
      buffer.putInt(index);
      buffer.putInt(offset * parent.subPieceLength);
      if (index * parent.subRatio + offset == parent.lastSubpiece) {
        buffer.putInt(parent.lastSubPieceLength);
      } else {
        buffer.putInt(parent.parent.SUBPIECELENGTH);
      }
      out.write(buffer.array());
    }
  }

  private class Request {
    private final int index;
    private final int offset;
    private final int length;

    public Request(int index, int offset, int length) {
      this.index = index;
      this.offset = offset;
      this.length = length;
    }

    public boolean equals(Object o) {
      if (o instanceof Request) {
        return (((Request) o).offset == offset)
            && (((Request) o).index == index)
            && (((Request) o).length == length);
      } else {
        return false;
      }
    }
  }
}
