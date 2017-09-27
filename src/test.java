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
