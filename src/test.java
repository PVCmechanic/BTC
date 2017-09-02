import java.io.IOException;

public class test {
    public static void main(String[] args) {
        Bencode bencode = new Bencode();
            byte[] arr = "li2e3:abcli3eee".getBytes();

        try {
            Object thing = bencode.bdecode(arr);
            System.out.println(thing);

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
