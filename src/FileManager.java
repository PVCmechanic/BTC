import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;

public class FileManager {
  public final int SUBPIECELENGTH = 16384;
  private final RandomAccessFile outFile;
  private final MessageDigest digest;
  private final byte[] pieceHashes;
  private final int numPieces;
  private final LinkedList<Integer> pieceOrder;
  private final byte[] bitfield;
  private final byte[] subBitfield;
  private final int subRatio;
  private final int pieceLength;
  private final int lastSubpiece;
  private final int lastSubpieceLength;
  private final int lastPieceLength;
  private int status;
  private final String fname;

  public FileManager(int pieceLength, byte[] pieceHashes, String fname, int flen)
      throws NoSuchAlgorithmException, IOException {

    digest = MessageDigest.getInstance("SHA-1");
    this.fname = fname;
    this.pieceLength = pieceLength;
    this.pieceHashes = pieceHashes;
    numPieces = pieceHashes.length / 20;
    outFile = new RandomAccessFile(fname, "rw");
    outFile.setLength(flen);
    subRatio = pieceLength / SUBPIECELENGTH;
    lastPieceLength = ((flen - 1) % pieceLength) + 1;
    lastSubpiece = flen / SUBPIECELENGTH;
    lastSubpieceLength = ((flen - 1) % SUBPIECELENGTH) + 1;
    int bitfieldLength = flen / (pieceLength * 8);
    if (flen % (pieceLength * 8) != 0) {
      bitfieldLength++;
    }
    bitfield = new byte[bitfieldLength];
    int subBitfieldLength = bitfieldLength * subRatio;
    subBitfield = new byte[subBitfieldLength];
    pieceOrder = new LinkedList<>();

    if (checkFile()) {
      System.out.println("File exists and is complete.");
      System.out.println("seeding");
    } else {
      // Generate and randomize the list of required pieces
      int numSubpieces = flen / SUBPIECELENGTH;
      if (flen % SUBPIECELENGTH != 0) {
        numSubpieces++;
      }
      for (int i = 0; i < numSubpieces; i++) {
        if (((subBitfield[i / 8] >> (7 - i % 8)) & 1) == 0) {
          pieceOrder.add(i);
        }
      }
      Collections.shuffle(pieceOrder);
    }

    System.out.printf(
        "Bitfield length: %d, Subbitfield length: %d, Pieces: %d\n",
        bitfieldLength, subBitfieldLength, numPieces);
  }

  /**
   * Get a file offset that is still needed to complete the file which the peer has.
   *
   * @param peerBitfield The bitfield of the pieces the peer has
   * @return The offset of a missing subpiece, -1 if all subpieces are downloaded
   */
  public synchronized int getRequest(byte[] peerBitfield) {
    if (pieceOrder.isEmpty()) {
      return -1;
    }
//    System.out.print("getRequest: \t");
//    for (byte piece : peerBitfield) {
//      System.out.print(
//          String.format("%8s", Integer.toBinaryString(piece & 0xff)).replace(' ', '0') + " ");
//    }
//    System.out.println();
    Iterator<Integer> iter = pieceOrder.iterator();
    int offset = iter.next();
    while (((peerBitfield[(offset / subRatio) / 8] >> (7 - (offset / subRatio) % 8)) & 1) == 0) {
      if (!iter.hasNext()) {
        return -1;
      }
      offset = iter.next();
    }
    iter.remove();
    return offset;
  }

  /**
   * @param index The index of the piece to be checked
   * @return If the piece exists
   */
  public synchronized boolean hasPiece(int index) {
    return ((bitfield[index / 8] >> (7 - index % 8)) & 1) == 1;
  }

  public synchronized byte[] getBitfield() {
    return bitfield;
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

  public synchronized boolean isDone() {
    return status == 1;
  }
  /**
   * Updates a subpiece of the file. A 16kB section is required per the protocol. If a piece fails
   * to validate, the corresponding subpiece indices are re-added to the list of required pieces.
   *
   * @param index The index of the piece to be updated
   * @param offset The offset within the piece to start updating from
   * @param data The data to write
   * @return If the piece is complete and validates
   */
  public synchronized boolean setPiece(int index, int offset, byte[] data) {
    int subIdx = offset / SUBPIECELENGTH;
    try {
      outFile.seek(index * pieceLength + offset);
      outFile.write(data);
      subBitfield[(index * subRatio + subIdx) / 8] |= (1 << 7 - (index * subRatio + subIdx) % 8);

      if (index == numPieces - 1) {
        for (int i = index * subRatio; i < lastSubpiece; i++) {
          if (((subBitfield[i / 8] >> (7 - i % 8)) & 1) == 0) {
            return false;
          }
        }
      } else {
        for (int i = index * subRatio; i < (index + 1) * subRatio; i++) {
          if (((subBitfield[i / 8] >> (7 - i % 8)) & 1) == 0) {
            return false;
          }
        }
      }

      if (verifyPiece(index)) {
        return true;
      } else {
        if (index == numPieces - 1) {
          for (int i = index * subRatio; i < lastSubpiece; i++) {
            subBitfield[i / 8] &= ~(1 << (7 - i % 8));
            pieceOrder.add(i);
          }
        } else {
          for (int i = index * subRatio; i < (index + 1) * subRatio; i++) {
            subBitfield[i / 8] &= ~(1 << (7 - i % 8));
            pieceOrder.add(i);
          }
        }
        return false;
      }

    } catch (IOException e) {
      System.out.println("IOException setting piece " + index + " at subindex " + subIdx);
      e.printStackTrace();
      // SHUTDOWN GRACEFULLY
      // *********************************************************
      return false;
    }
  }

  /**
   * Read a section of data from the file. The specified section must have already been successfully
   * downloaded and validated. Does not enforce the 16 kB request standard.
   *
   * @param index The index of the requested piece
   * @param offset The offset within the piece to start reading from
   * @param length The amount of data to read
   * @return A byte array containing the requested data
   */
  public synchronized byte[] readPiece(int index, int offset, int length) {
    if (((bitfield[index / 8] >> (7 - index % 8)) & 1) == 1) {
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

  /**
   * Checks a piece against the corresponding hash. If the piece is not fully downloaded, no action
   * will be taken. Updates the bitfield if the piece validates, resets the corresponding
   * sub-bitfield bits if the piece fails validation. Improperly downloaded pieces will not be
   * removed from disk.
   *
   * @param index The index to be validated
   * @return True if the piece successfully validates, false if the piece fails validation or is not
   *     fully downloaded
   * @throws IOException The file is invalid
   */
  private boolean verifyPiece(int index) throws IOException {
    int dataLength;
    if (index == numPieces - 1) {
      dataLength = lastPieceLength;
    } else {
      dataLength = pieceLength;
    }
    outFile.seek(index * pieceLength);
    byte[] data = new byte[dataLength];
    outFile.read(data);
    byte[] hash = digest.digest(data);
    byte[] goal = Arrays.copyOfRange(pieceHashes, index * 20, (index + 1) * 20);
    if (Arrays.equals(hash, goal)) {
      //      System.out.printf("\tPIECE %d VERIFIED\n", index);
      bitfield[index / 8] |= (1 << (7 - index % 8));
      updateStatus();
      return true;
    } else {
      System.out.printf("\t*>*>BAD PIECE %d<*<*\n", index);
      return false;
    }
  }

  private void updateStatus() {
    int count = 0;
    for (int i = 0; i < numPieces; i++) {
      if (((bitfield[i / 8] >> (7 - i % 8)) & 1) == 1) {
        count++;
      }
    }
    if (count % (numPieces / 30) == 0) {
      System.out.printf("-------------------%s %02d%% complete\n", fname, count * 100 / numPieces);
    }
    status = count / numPieces;
  }

  private boolean checkFile() throws IOException {
    for (int i = 0; i < numPieces; i++) {
      if (!verifyPiece(i)) {
        return false;
      }
    }
    return true;
  }

  public boolean peerHasMissing(byte[] peerBitfield) {
//    System.out.print("peerHasMissing:\t");
//    for (byte piece : peerBitfield) {
//      System.out.print(
//          String.format("%8s", Integer.toBinaryString(piece & 0xff)).replace(' ', '0') + " ");
//    }
//    System.out.println();
    for (int i = 0; i < bitfield.length; i++) {
      if ((peerBitfield[i] & 0xff) > (bitfield[i] & 0xff)) {
        return true;
      }
    }
    return false;
  }
}
