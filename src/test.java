import java.io.IOException;

public class test {
    public static void main(String[] args) {
        Bencode bencode = new Bencode();
            byte[] arr = "l10:abcdefghikei11e".getBytes();

        try {
            Object thing = bencode.bdecode(arr);
            System.out.println(thing);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
