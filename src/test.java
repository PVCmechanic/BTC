<<<<<<< HEAD
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
=======
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class test {
    public static void main(String[] args) {
        Bencode bencode = new Bencode();
            byte[] arr = "li23ed3:abci54e11:aaaaaaaaaaali3ei4ei5eeei1ee".getBytes();

        try {
            HashMap<byte[],Object> a = new HashMap<>();
            ArrayList<Object> b = new ArrayList<>();
            b.add(35);
            b.add(931);
            b.add(new byte[]{24,43,44,62});
            a.put(new byte[]{110,117,109},b);
            a.put(new byte[]{115,116,114,105,110,103},new byte[]{118,97,108,117,101});
            a.put(new byte[]{111,116,104,101,114},907);
            byte[] out = bencode.bencode(a);
            System.out.println(Arrays.toString(out));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }
}
>>>>>>> 49623dbf354f84545c2c5a481310783f269533e2
