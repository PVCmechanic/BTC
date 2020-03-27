import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;

public class test {
    public static void main(String[] args) {
        try {
            Bencode bencode = new Bencode(new File("twinprinces2.jpg.torrent"));
            bencode.trackerConnect();
            bencode.peerConnect();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }
}
