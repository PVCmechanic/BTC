import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

public class test {
    public static void main(String[] args) {
        Bencode bencode = new Bencode();
            byte[] arr = "l5:helloi52ee".getBytes();

        try {
            Object thing = bencode.bdecode(arr);
            System.out.println(thing);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
