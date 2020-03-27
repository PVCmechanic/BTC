import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;

public class FileManager {
  public final int SUBPIECELENGTH = 16384;
  private int numPieces;
  private LinkedList<Integer> pieceOrder;
  private byte[] bitfield;
  private byte[] subBitfield;
  private int subRatio;
  private RandomAccessFile outFile;
  private byte[] pieceHashes;
  private MessageDigest digest;
  private int pieceLength;
  private int lastSubpiece;
  private int lastSubpieceLength;
  private int lastPieceLength;
  private int status;
  private String fname;

  public FileManager(int pieceLength, byte[] pieceHashes, String fname, int flen)
      throws NoSuchAlgorithmException, IOException {
    this.fname = fname;
    digest = MessageDigest.getInstance("SHA-1");
    outFile = new RandomAccessFile(fname, "rw");
    outFile.setLength(flen);
    this.pieceLength = pieceLength;
    numPieces = pieceHashes.length / 20;
    int bitfieldLength = flen / (pieceLength * 8);
    if (flen % (pieceLength * 8) != 0) {
      bitfieldLength++;
    }
    bitfield = new byte[bitfieldLength];
    subRatio = pieceLength / SUBPIECELENGTH;
    int subBitfieldLength = bitfieldLength * subRatio;
    subBitfield = new byte[subBitfieldLength];
    pieceOrder = new LinkedList<>();
    int numSubpieces = flen / SUBPIECELENGTH;
    if (flen % SUBPIECELENGTH != 0) {
      numSubpieces++;
    }
    for (int i = 0; i < numSubpieces; i++) {
      pieceOrder.add(i);
    }
    Collections.shuffle(pieceOrder);
    this.pieceHashes = pieceHashes;
    lastSubpiece = flen / SUBPIECELENGTH;
    lastSubpieceLength = flen % SUBPIECELENGTH;
    lastPieceLength = flen % pieceLength;
    System.out.printf(
        "Bitfield length: %d, Subbitfield length: %d, Pieces: %d\n",
        bitfieldLength, subBitfieldLength, numPieces);
  }

  public synchronized int getRequest() {
    if (pieceOrder.isEmpty()) {
      return -1;
    }
    return pieceOrder.remove();
  }

  public synchronized boolean hasPiece(int index) {
    return ((bitfield[index / 8] >> (index % 8)) & 1) == 1;
  }

  public synchronized byte[] getBitfield() {
    return bitfield;
  }

  public int getNumPieces() {
    return numPieces;
  }

  public int getSubRatio() {
    return subRatio;
  }

  public int getLastSubpiece() {
    return lastSubpiece;
  }

  public int getLastSubpieceLength() {
    return lastSubpieceLength;
  }

  public synchronized boolean setPiece(int index, int offset, byte[] data) {
    int subIndex = offset / SUBPIECELENGTH;
    try {
      outFile.seek(index * pieceLength + offset);
      outFile.write(data);
      subBitfield[(index * subRatio + subIndex) / 8] |= (1 << (index * subRatio + subIndex) % 8);
      if (!verifyPiece(index)) {
        pieceOrder.add(index * subRatio + offset);
      }
      if(status == 1) {
        return checkFile();
      }
      return false;
    } catch (IOException e) {
      System.out.println("IOException setting piece " + index + " at subindex " + subIndex);
      e.printStackTrace();
      // SHUTDOWN GRACEFULLY
      // *********************************************************
      return false;
    }
  }

  public synchronized byte[] readPiece(int index, int offset, int length) {
    if (((bitfield[index / 8] >> (index % 8)) & 1) == 1) {
      try {
        outFile.seek(index * pieceLength + offset);
        byte[] data = new byte[length];
        if (outFile.read(data) == -1) {
          System.out.println("EOF reached");
          throw new IOException();
        }
        return data;

      } catch (IOException e) {
        System.out.println(
            "IOException reading piece " + index + " at offset " + offset + " of length " + length);
        e.printStackTrace();
        // SHUTDOWN GRACEFULLY
        // *********************************************************
      }
    }
    System.out.println("Unvalidated piece " + index);
    throw new IllegalArgumentException();
  }

  private boolean verifyPiece(int index) throws IOException {
    int dataLength;
    if (index == numPieces - 1) {
      for (int i = index * subRatio; i < lastSubpiece; i++) {
        if (((subBitfield[i / 8] >> (i % 8)) & 1) == 0) {
          return true;
        }
      }
      dataLength = lastPieceLength;
    } else {
      for (int i = index * subRatio; i < (index + 1) * subRatio; i++) {
        if (((subBitfield[i / 8] >> (i % 8)) & 1) == 0) {
          return true;
        }
      }
      dataLength = pieceLength;
    }
    outFile.seek(index * pieceLength);
    byte[] data = new byte[dataLength];
    outFile.read(data);
    byte[] hash = digest.digest(data);
    byte[] goal = Arrays.copyOfRange(pieceHashes, index * 20, (index + 1) * 20);
    if (Arrays.equals(hash, goal)) {
      System.out.printf("\tPIECE %d\tVERIFIED\n", index);
      bitfield[index / 8] |= (1 << (index % 8));
      printStatus();
      return true;
    } else {
      System.out.printf("\t*>*>BAD PIECE %d<*<*\n", index);
      for (int i = index * subRatio; i < (index + 1) * subRatio; i++) {
        subBitfield[i / 8] &= ~(1 << (i % 8));
      }
      return false;
    }
  }

  private void printStatus() {
    int count = 0;
    for (int i = 0; i < numPieces; i++) {
      if (((bitfield[i / 8] >> (i % 8)) & 1) == 1) {
        count++;
      }
    }
    System.out.printf("------------------- %02d%% complete\n", count * 100 / numPieces);
    status = count/numPieces;
  }

  private boolean checkFile() throws IOException {
    boolean toRet = true;
    for (int i = 0; i < numPieces; i++) {
      if (!verifyPiece(i)) {
        bitfield[i / 8] |= (1 << (7 - i % 8));
        toRet = false;
      }
    }
    System.out.println("********************");
    System.out.printf("Finished downloading %s\n", fname);
    System.out.println("********************");
    return toRet;
  }
}
