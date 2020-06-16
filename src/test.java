import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Scanner;

public class test {
  public static void main(String[] args) {
    try {
      File wd = new File(".");
      File[] files =
          wd.listFiles(pathname -> pathname.toString().toLowerCase().contains(".torrent"));
      if (files == null) {
        throw new IOException("Something failed");
      }
      if (files.length == 0) {
        System.out.println("No torrent files found");
      }
      for (int i = 1; i < files.length+1; i++) {
        System.out.printf("%d: %s\n", i, files[i-1].getName());
      }
      System.out.println("Which file?");

      Scanner input = new Scanner(System.in);
      int choice = input.nextInt();

      while(choice<1 || choice>files.length) {
        choice = input.nextInt();
      }

      File torrentFile = files[choice-1];
      Bencode bencode = new Bencode(torrentFile);
      bencode.trackerConnect();
      bencode.peerConnect();
      bencode.listen();
    } catch (IllegalArgumentException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
  }
}
