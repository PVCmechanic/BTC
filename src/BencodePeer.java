import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

public class BencodePeer implements Runnable {

  public final String HANDSHAKE = "BitTorrent protocol";
  public final byte[] RESERVED = new byte[8];
  private InetSocketAddress ip;
  private byte[] infoHash;
  private byte[] id;
  private boolean choking;
  private boolean interested;
  private boolean peerChoking;
  private boolean peerInterested;
  private Queue<Request> requests;
  private int numPieces;
  private byte[] peerBitfield;
  private byte[] bitfield;
  private Bencode parent;
  private int subRatio;
  private long time;
  private long lastRequest;
  private int lastNew;
  private int tens;
  private FileManager mgr;
  private int subPieceLength;
  private boolean done;

  private int lastSubpiece;
  private int lastSubPieceLength;

  public BencodePeer() {
    choking = true;
    interested = false;
    peerChoking = true;
    peerInterested = false;
    requests = new LinkedList<>();
    time = 0;
    lastNew = -1;
    tens = 0;
    done = false;
  }

  public BencodePeer(
      InetSocketAddress ip, byte[] infoHash, byte[] id, Bencode parent, FileManager mgr) {
    this();
    this.ip = ip;
    this.infoHash = infoHash;
    this.id = id;
    this.parent = parent;
    this.bitfield = mgr.getBitfield();
    peerBitfield = new byte[this.bitfield.length];
    this.mgr = mgr;
    numPieces = mgr.getNumPieces();
    subRatio = mgr.getSubRatio();
    lastSubpiece = mgr.getLastSubpiece();
    lastSubPieceLength = mgr.getLastSubpieceLength();
    subPieceLength = mgr.SUBPIECELENGTH;
  }

  public void run() {

    System.out.println("Opening connection with: " + ip);
    try (Socket socket = new Socket()) {
      socket.connect(ip, 5000);
      try (DataInputStream in = new DataInputStream(socket.getInputStream());
          OutputStream out = socket.getOutputStream()) {
        handshake(infoHash, id, out);
        String ips = ip.getHostString();

        if (bitfield != null) {
          bitfield(out);
        }
        int pstrlen = in.read();
        if (pstrlen == 0) {
          System.out.printf("%s closed?\n", ips);
        }
        byte[] pstr = new byte[pstrlen];
        in.readFully(pstr);
        byte[] reservd = new byte[8];
        in.readFully(reservd);
        byte[] peerHash = new byte[20];
        in.readFully(peerHash);
        byte[] peerId = new byte[20];
        in.readFully(peerId);
        if (!Arrays.equals(peerHash, infoHash)) {
          System.out.println("bad hash");
          throw new IOException();
        }
        int len;
        int read = 0;
        byte[] lenBytes = new byte[4];
        while (true) {

          read += in.read(lenBytes, read, 4 - read);
          if (read == 4) {
            read = 0;
            len = networkInt(lenBytes[0], lenBytes[1], lenBytes[2], lenBytes[3]);
            System.out.printf("%s length %d\n", ips, len);
            if (len > 0) {
              handleInput(in, len);
            }
          }

          // *******************
          // Implement HAVE update from bitfield and other downloaded pieces

          if (!requests.isEmpty() && !choking && peerInterested) {
            Request request = requests.remove();
            piece(request, out);
          }

          if (!done) {
            bitfield = mgr.getBitfield();
            if (!interested) {
              for (int i = 0; i < numPieces; i++) {
                if (((peerBitfield[i / 8] >> (i % 8)) & 1) == 1
                    && (bitfield[i / 8] & (1 << i % 8)) == 0) {
                  System.out.printf("Interested in piece %d\n", i);
                  interested(out);
                  interested = true;
                  break;
                }
              }
            }
            if (!peerChoking && interested) {
              int offset = mgr.getRequest();
              if (offset >= 0) {
                int index = offset / subRatio;
                int subIndex = offset % subRatio;
                request(index, subIndex, out);
              } else {
                interested = false;
                System.out.println("done?");
              }
            }
          }  // IF_DONE
          if (System.currentTimeMillis() - time > 5000) {
            time = System.currentTimeMillis();
            tens++;
            if ((parent.getUnchoked() < 4 || peerInterested) && choking) {
              choking = false;
              unchoke(out);
              parent.incUnchoked();
            }
          }
          Thread.sleep(10);
        }
      }

    } catch (SocketTimeoutException e) {
      System.out.printf("Timeout on connection to %s\n", ip);
    } catch (IOException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      System.out.println("Interrupted");
      e.printStackTrace();
    } catch (Exception e) {
      System.out.println("Exception");
    } finally {
      parent.decThreads();
    }
  }

  /**
   * Interprets and handles a single bittorrent peer protocol packet
   * @param in The socket to read from
   * @param len The value of the length header
   * @throws IOException The socket is invalid
   */
  private void handleInput(DataInputStream in, int len) throws IOException {
    int messageId = in.read();
    byte[] data = new byte[len - 1];
    in.readFully(data);
    System.out.print("received ");
    switch (messageId) {
      case 0:
        System.out.println("choking");
        peerChoking = true;
        break;
      case 1:
        System.out.println("unchoking");
        peerChoking = false;
        break;
      case 2:
        System.out.println("interested");
        peerInterested = true;
        break;
      case 3:
        System.out.println("disinterested");
        peerInterested = false;
        break;
      case 4:
        {
          System.out.println("have");
          int index = networkInt(data[0], data[1], data[2], data[3]);
          peerBitfield[index / 8] |= (1 << (index % 8));
          break;
        }
      case 5:
        System.out.println("bitfield");
        peerBitfield = data;
        break;
      case 6:
        {
          System.out.println("request");
          int index = networkInt(data[0], data[1], data[2], data[3]);
          int offset = networkInt(data[4], data[5], data[6], data[7]);
          int length = networkInt(data[8], data[9], data[10], data[11]);
          if (mgr.hasPiece(index)) {
            requests.add(new Request(index, offset, length));
          }
          break;
        }
      case 7:
        {
          System.out.println("piece");
          int index = networkInt(data[0], data[1], data[2], data[3]);
          int offset = networkInt(data[4], data[5], data[6], data[7]);
          byte[] subpiece = Arrays.copyOfRange(data, 8, data.length);
          done = mgr.setPiece(index, offset, subpiece);
          break;
        }
      case 8:
        {
          System.out.println("cancel");
          int index = networkInt(data[0], data[1], data[2], data[3]);
          int offset = networkInt(data[4], data[5], data[6], data[7]);
          int length = networkInt(data[8], data[9], data[10], data[11]);
          requests.remove(new Request(index, offset, length));
          break;
        }
      default:
        System.out.printf("BAD from %s\n", ip);
    }
  }

  private void handshake(byte[] infoHash, byte[] id, OutputStream out) throws IOException {
    int size = 1 + HANDSHAKE.length() + RESERVED.length + infoHash.length + id.length;
    ByteBuffer buf = ByteBuffer.allocate(size);
    buf.put((byte)HANDSHAKE.length());
    buf.put(HANDSHAKE.getBytes());
    buf.put(RESERVED);
    buf.put(infoHash);
    buf.put(id);
    out.write(buf.array());
  }

  private void choke(OutputStream out) throws IOException {
    System.out.println("Sent CHOKING");
    out.write(new byte[] {0, 0, 0, 1, 0});
  }

  private void unchoke(OutputStream out) throws IOException {
    System.out.println("Sent UNCHOKING");
    out.write(new byte[] {0, 0, 0, 1, 1});
  }

  private void interested(OutputStream out) throws IOException {
    System.out.println("Sent INTERESTED");
    out.write(new byte[] {0, 0, 0, 1, 2});
  }

  private void disinterested(OutputStream out) throws IOException {
    System.out.println("Sent DISINTERESTED");
    out.write(new byte[] {0, 0, 0, 1, 3});
  }

  private void have(int index, OutputStream out) throws IOException {
    System.out.println("Sent HAVE");
    ByteBuffer buffer = ByteBuffer.allocate(9);
    buffer.putInt(5);
    buffer.put((byte) 5);
    buffer.putInt(index);
    out.write(buffer.array());
  }

  private void bitfield(OutputStream out) throws IOException {
    int len = bitfield.length + 1;
    ByteBuffer buf = ByteBuffer.allocate(4 + len);
    buf.putInt(len);
    buf.put((byte) 5);
    buf.put(bitfield);
    System.out.printf("Sent BITFIELD, data: %s\n", Arrays.toString(buf.array()));
    out.write(buf.array());
  }

  private void piece(Request request, OutputStream out) throws IOException {
    System.out.printf("Sent PIECE, len: %d\n", request.length);
    byte[] data = mgr.readPiece(request.index, request.offset, request.length);
    ByteBuffer buffer = ByteBuffer.allocate(13 + data.length);
    buffer.putInt(9 + data.length);
    buffer.put((byte) 7);
    buffer.putInt(request.index);
    buffer.putInt(request.offset);
    buffer.put(data);
    out.write(buffer.array());
  }

  private void request(int index, int subIndex, OutputStream out) throws IOException {
    System.out.printf("Sent REQUEST: piece %d @ %d\n", index, subIndex);
    ByteBuffer buffer = ByteBuffer.allocate(17);
    buffer.putInt(13);
    buffer.put((byte) 6);
    buffer.putInt(index);
    buffer.putInt(subIndex * subPieceLength);
    if (index * subRatio + subIndex == lastSubpiece) {
      buffer.putInt(lastSubPieceLength);
    } else {
      buffer.putInt(parent.SUBPIECELENGTH);
    }
    out.write(buffer.array());
    lastRequest = System.currentTimeMillis();
  }

  private static int networkInt(int a, int b, int c, int d) {
    return (d & 0xff) | ((c & 0xff) << 8) | ((b & 0xff) << 16) | ((a & 0xff) << 24);
  }

  private class Request {
    private int index;
    private int offset;
    private int length;

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
