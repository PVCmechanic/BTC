import java.io.IOException;

public class test {
    public static void main(String[] args) {
        Bencode bencode = new Bencode();
            byte[] arr = "li23ed3:abci54e11:aaaaaaaaaaali3ei4ei5eeei1ee".getBytes();

        try {
            Object thing = bencode.bdecode(arr);
            System.out.println(thing);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
