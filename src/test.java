import java.io.IOException;
import java.util.Arrays;

public class test {
    public static void main(String[] args) {
        Bencode bencode = new Bencode();
            byte[] arr = "li23ed3:abci54e11:aaaaaaaaaaali3ei4ei5eeei1ee".getBytes();

        try {
            byte[] a = new byte[]{45,52,31,41};
            byte[] out = bencode.bencode(a);
            System.out.println(String.valueOf(out));
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        }
    }
}
