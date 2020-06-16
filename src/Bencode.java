import java.net.ServerSocket;
import java.net.Socket;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Bencode {
  public final int SUBPIECELENGTH = 16384;
  private final String TRACKERREQ =
      "%s?info_hash=%s&peer_id=%s&port=%s&uploaded=%s&downloaded=%s&left=%s&event=%s&compact=1&numwant=10";
  private String announce;
  private SortedMap<String, Object> info;
  private List<Map<String, String>> peers;
  private List<BencodePeer> connections;
  private byte[] infoHash;
  private int pieceLength;
  private long up;
  private long down;
  private long left;
  private String id;
  private String event;
  private long interval;
  private ExecutorService executor;
  private MessageDigest digest;
  private int numunchoked;
  private final Object chokedLock;
  private int updated;
  private int threads;
  private final Object threadsLock;
  private FileManager mgr;

  public Bencode() throws NoSuchAlgorithmException {
    up = 0;
    down = 0;
    left = 0;
    peers = new ArrayList<>();
    connections = new ArrayList<>();
    id = genID();
    executor = Executors.newCachedThreadPool();
    digest = MessageDigest.getInstance("SHA-1");
    numunchoked = 0;
    chokedLock = new Object();
    updated = 0;
    threads = 0;
    threadsLock = new Object();

    System.out.println(id);
  }

  public Bencode(File file) throws IOException, NoSuchAlgorithmException {
    this();
    initialize(file);
  }

  public void initialize(File file) throws IOException, NoSuchAlgorithmException {
    FileInputStream stream = new FileInputStream(file);
    SortedMap<String, Object> dec = (SortedMap<String, Object>) decode(IOUtils.toByteArray(stream));
    announce = new String((byte[]) dec.get("announce"));
    info = (SortedMap<String, Object>) dec.get("info");
    infoHash = digest.digest(encode(info));
    byte[] pieces = (byte[]) info.get("pieces");
    String fname = new String((byte[]) info.get("name"));
    int flen = Math.toIntExact((long) info.get("length"));
    pieceLength = Math.toIntExact((long) info.get("piece length"));
    mgr = new FileManager(pieceLength, pieces, fname, flen);

    System.out.printf(
        "Announce: %s, Length: %d, Pieces: %d, Piece length: %d\n",
        announce, flen, pieces.length / 20, pieceLength);
  }

  public String getID() {
    return id;
  }

  public void incUnchoked() {
    synchronized (chokedLock) {
      numunchoked++;
    }
  }

  public void decUnchoked() {
    synchronized (chokedLock) {
      numunchoked--;
    }
  }

  public int getUnchoked() {
    synchronized (chokedLock) {
      return numunchoked;
    }
  }

  public void incThreads() {
    synchronized (threadsLock) {
      threads++;
    }
  }

  public void decThreads() {
    synchronized (threadsLock) {
      threads--;
    }
  }

  public void sendHas(int index) {
    for (BencodePeer peer : connections) {
      peer.newPiece(index);
    }
  }

  /**
   * Connects to the tracker of the provided .torrent file and stores the returned peers. The client
   * must have been initialized before this method may be called.
   */
  public void trackerConnect() {
    try {
      event = "start";
      left = (long) info.get("length");
      String fullUrl =
          String.format(
              TRACKERREQ,
              announce,
              // URLEncoder.encode(new String(infoHash), StandardCharsets.UTF_8),
              urlEncode(infoHash),
              id,
              6881,
              up,
              down,
              left,
              event);

      URLConnection con = new URL(fullUrl).openConnection();
      InputStream data = con.getInputStream();
      byte[] out = IOUtils.toByteArray(data);
      Object decoded = decode(out);
      interval = (long) ((TreeMap) decoded).get("interval");
      if (((TreeMap) decoded).get("peers") instanceof byte[]) {
        byte[] peersData = (byte[]) ((TreeMap) decoded).get("peers");
        for (int i = 0; i < peersData.length; i += 6) {
          Map<String, String> tmp = new HashMap<String, String>();
          String ip =
              String.format(
                  "%d.%d.%d.%d",
                  0xff & peersData[i],
                  0xff & peersData[i + 1],
                  0xff & peersData[i + 2],
                  0xff & peersData[i + 3]);

          tmp.put("ip", ip);
          ByteBuffer buf = ByteBuffer.wrap(Arrays.copyOfRange(peersData, i + 4, i + 6));
          tmp.put("port", Integer.toString((int) buf.getChar()));
          tmp.put("peer_id", "");
          if (!tmp.get("port").equals("6881")) {
            System.out.printf("Peer %d: IP: %s, Port: %s\n", i/6, ip, tmp.get("port"));
            peers.add(tmp);
          }
        }
      } else {
        List<Map> peerList = (List) ((TreeMap) decoded).get("peers");
        for (Map m : peerList) {
          Map<String, String> tmp = new HashMap<>();
          tmp.put("ip", new String((byte[]) m.get("ip")));
          tmp.put("port", m.get("port").toString());
          tmp.put("peer_id", "");
          if (!tmp.get("port").equals("6881")) {
            System.out.printf("Peer: IP: %s, Port: %s\n", tmp.get("ip"), tmp.get("port"));
            peers.add(tmp);
          }
        }
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * Connects to every peer returned from the tracker. Each connection is handled by a separate
   * thread.
   */
  public void peerConnect() {
    for (Map<String, String> peer : peers) {
      InetSocketAddress peerAddr =
          new InetSocketAddress(peer.get("ip"), Integer.parseInt(peer.get("port")));
      try {
        BencodePeer peerCon = new BencodePeer(peerAddr, infoHash, id.getBytes(), this, mgr);
        connections.add(peerCon);
        incThreads();
        executor.submit(peerCon);
      } catch (IOException e) {
        System.out.printf("Peer %s timed out\n", peer.get("ip"));
      }
    }
  }

  public void listen() {
    try (ServerSocket server = new ServerSocket(6881)) {
      while (true) {
        Socket sock = server.accept();
        System.out.println("accpted peer");
        BencodePeer peerCon = new BencodePeer(sock, infoHash, id.getBytes(), this, mgr);
        connections.add(peerCon);
        incThreads();
        executor.submit(peerCon);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private String genID() {
    Random random = new Random();
    byte[] idPart = new byte[12];
    for (int i = 0; i < 12; i++) {
      byte tmp = (byte) (random.nextInt(62));
      if (tmp < 10) {
        idPart[i] = (byte) (tmp + 48);
      } else if (tmp < 36) {
        idPart[i] = (byte) (tmp + 55);
      } else {
        idPart[i] = (byte) (tmp + 61);
      }
    }
    return "-0x0000-" + new String(idPart);
  }

  public static Object decode(byte[] input) throws IllegalArgumentException {
    if (input[0] >= '0' && input[0] <= '9') {
      int pointer = 1;
      while (input[pointer] != ':') {
        pointer++;
      }
      return Arrays.copyOfRange(input, pointer + 1, input.length);
    } else if (input[0] == 'i') {
      return Long.valueOf(new String(Arrays.copyOfRange(input, 1, input.length - 1)));
    } else if (input[0] == 'l' || input[0] == 'd') {
      boolean list = input[0] == 'l';
      input = Arrays.copyOfRange(input, 1, input.length - 1);
      List<Object> out = new ArrayList<>();
      while (true) {
        int pointer = 1;
        if (input[0] == 'i') {
          while (input[pointer] != 'e') {
            pointer++;
          }
          out.add(decode(Arrays.copyOfRange(input, 0, pointer + 1)));
          input = Arrays.copyOfRange(input, pointer + 1, input.length);
        } else if (input[0] == 'd' || input[0] == 'l') {
          int indent = 0;
          boolean searching = true;
          while (searching) {
            if (input[pointer] == 'i') {
              while (input[pointer] != 'e') {
                pointer++;
              }
            } else if (input[pointer] == 'l' || input[pointer] == 'd') {
              indent++;
            } else if (input[pointer] == 'e') {
              indent--;
              if (indent == -1) {
                searching = false;
              }
            } else {
              int newpoint = pointer;
              while (input[newpoint] != ':') {
                newpoint++;
              }
              byte[] nextSection = Arrays.copyOfRange(input, pointer, newpoint);
              pointer = newpoint + Integer.parseInt(new String(nextSection));
            }
            pointer++;
          }
          out.add(decode(Arrays.copyOfRange(input, 0, pointer)));
          input = Arrays.copyOfRange(input, pointer, input.length);
        } else {
          while (input[pointer] != ':') {
            pointer++;
          }
          int length = Integer.parseInt(new String(Arrays.copyOfRange(input, 0, pointer)));
          out.add(decode(Arrays.copyOfRange(input, 0, length + pointer + 1)));
          input = Arrays.copyOfRange(input, length + pointer + 1, input.length);
        }
        if (input.length == 0) {
          if (list) {
            return out;
          } else {
            SortedMap<String, Object> map = new TreeMap<>();
            for (int i = 0; i < out.size(); i += 2) {
              map.put(new String((byte[]) out.get(i)), out.get(i + 1));
            }
            return map;
          }
        }
      }
    }
    throw new IllegalArgumentException();
  }

  public static byte[] encode(Object input) throws IllegalArgumentException {
    if (input instanceof Long) {
      byte[] num = input.toString().getBytes();
      byte[] out = new byte[num.length + 2];
      out[0] = 'i';
      for (int i = 0; i < num.length; i++) {
        out[i + 1] = num[i];
      }
      out[out.length - 1] = 'e';
      return out;
    } else if (input instanceof List || input instanceof SortedMap) {
      List<byte[]> outPieces = new ArrayList();
      if (input instanceof List) {
        Object[] parts = ((List) input).toArray();
        for (int i = 0; i < parts.length; i++) {
          outPieces.add(encode(parts[i]));
        }
      } else {
        Object[] keys = ((SortedMap<String, Object>) input).keySet().toArray();
        Object[] parts = ((SortedMap<String, Object>) input).values().toArray();
        for (int i = 0; i < parts.length; i++) {
          outPieces.add(encode(keys[i]));
          outPieces.add(encode(parts[i]));
        }
      }

      int outLen = 2;
      for (int i = 0; i < outPieces.size(); i++) {
        outLen += outPieces.get(i).length;
      }
      byte[] out = new byte[outLen];
      if (input instanceof List) {
        out[0] = 'l';
      } else {
        out[0] = 'd';
      }
      out[out.length - 1] = 'e';
      int pointer = 1;
      for (int i = 0; i < outPieces.size(); i++) {
        for (int j = 0; j < outPieces.get(i).length; j++) {
          out[pointer] = outPieces.get(i)[j];
          pointer++;
        }
      }
      return out;
    } else if (input instanceof byte[] || input instanceof String) {
      if (input instanceof String) {
        input = ((String) input).getBytes();
      }
      int len = ((byte[]) input).length;
      int lenlen = String.valueOf(len).length();
      byte[] out = new byte[len + lenlen + 1];
      for (int i = 0; i < lenlen; i++) {
        out[i] = (byte) String.valueOf(len).charAt(i);
      }
      out[lenlen] = ':';
      for (int i = lenlen + 1; i < out.length; i++) {
        out[i] = ((byte[]) input)[i - lenlen - 1];
      }
      return out;
    } else {
      throw new IllegalArgumentException();
    }
  }

  private String urlEncode(byte[] in) {
    StringBuilder hashString = new StringBuilder();
    for (byte b : in) {
      if (b >= '0' && b <= '9'
          || b >= 'A' && b <= 'Z'
          || b >= 'a' && b <= 'z'
          || b == '.'
          || b == '-'
          || b == '_'
          || b == '~') {
        hashString.append((char) b);
      } else {
        hashString.append(String.format("%%%02X", b));
      }
    }
    return hashString.toString();
  }
}
