import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

public class test {
    public static void main(String[] args) {
        Bencode bencode = new Bencode();
        byte[] arr = {11,32,41};

        try {
            bencode.bdecode(arr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
