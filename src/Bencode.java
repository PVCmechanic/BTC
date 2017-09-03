import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.IOUtils;


public class Bencode {
    public Bencode() {
    }

    public Object bdecode(byte[] input2) throws IOException {
        /*InputStream inputStream = new FileInputStream("C:\\Users\\Max\\Documents\\BitTorrentClient\\NPR_1_archive.torrent");

        byte[] input2 = IOUtils.toByteArray(inputStream);
        */
        String in = new String(input2, "ISO-8859-1");
        if (input2[0] >= '0' && input2[0] <= '9') {
            int pointer = 1;
            while (input2[pointer] != ':') {
                pointer++;
            }
            int length = Integer.valueOf(new String(Arrays.copyOfRange(input2, 0, pointer)));
            return Arrays.copyOfRange(input2, pointer + 1, pointer + length + 1);
        } else {
            if (input2[0] == 'l' || input2[0] == 'd') {
                boolean list = input2[0] == 'l';
                input2 = Arrays.copyOfRange(input2, 1, input2.length - 1);
                List<Object> out = new ArrayList<>();
                while (true) {
                    int pointer = 1;
                    if (input2[0] == 'i') {
                        while (input2[pointer] != 'e') {
                            pointer++;
                        }
                        out.add(bdecode(Arrays.copyOfRange(input2, 0, pointer + 1)));
                        input2 = Arrays.copyOfRange(input2, pointer + 1, input2.length);
                    } else if (input2[0] == 'd' || input2[0] == 'l') {
                        int indent = 0;
                        boolean searching = true;
                        while (searching) {
                            if (input2[pointer] == 'i' || input2[pointer] == 'l' || input2[pointer] == 'd') {
                                indent++;
                            } else if (input2[pointer] == 'e') {
                                indent--;
                                if (indent == -1) {
                                    searching = false;
                                }
                            } else if (indent == 0) {
                                int newpoint = pointer;
                                while (input2[newpoint] != ':') {
                                    newpoint++;
                                }
                                pointer = newpoint + Integer.valueOf(new String(Arrays.copyOfRange(input2, pointer, newpoint)));
                            }
                            pointer++;
                        }
                        out.add(bdecode(Arrays.copyOfRange(input2, 0, pointer)));
                        input2 = Arrays.copyOfRange(input2, pointer, input2.length);
                    } else {
                        while (input2[pointer] != ':') {
                            pointer++;
                        }
                        int length = Integer.valueOf(new String(Arrays.copyOfRange(input2, 0, pointer)));
                        out.add(bdecode(Arrays.copyOfRange(input2, 0, length + pointer + 1)));
                        input2 = Arrays.copyOfRange(input2, length + pointer + 1, input2.length);
                    }
                    if (input2.length == 0) {
                        if (list) {
                            return out;
                        } else {
                            HashMap<byte[], Object> map = new HashMap<>();
                            for (int i = 0; i < out.size(); i += 2) {
                                map.put((byte[]) out.get(i), out.get(i + 1));
                            }
                            return map;
                        }
                    }
                }
            } else if (input2[0] == 'i') {
                return Integer.valueOf(new String(Arrays.copyOfRange(input2, 1, input2.length - 1)));

            }
        }

        return null;

    }
}
