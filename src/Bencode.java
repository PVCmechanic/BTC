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
        InputStream inputStream = new FileInputStream("C:\\Users\\Max\\Documents\\BitTorrentClient\\NPR_1_archive.torrent");

        byte[] inputa2 = IOUtils.toByteArray(inputStream);

        String in = new String(input2, "ISO-8859-1");
        if (input2[0] >= '0' && input2[0] <= '9') {
            int pointer = 1;
            while (input2[pointer] != ':') {
                pointer++;
            }
            int length = Integer.valueOf(new String(Arrays.copyOfRange(input2, 0, pointer)));
            return Arrays.copyOfRange(input2, pointer + 1, pointer + length + 1);
        } else {
            if (input2[0] == 'l') {
                input2 = Arrays.copyOfRange(input2, 1, input2.length - 1);
                List<Object> out = new ArrayList<>();
                boolean running = true;
                while (running) {
                    int pointer = 1;
                    if (input2[0] == 'i' || input2[0] == 'd' || input2[0] == 'l') {
                        while (input2[pointer] != 'e') {
                            pointer++;
                        }
                        out.add(bdecode(Arrays.copyOfRange(input2, 0, pointer)));
                        input2 = Arrays.copyOfRange(input2, pointer + 1, input2.length);
                    } else {
                        while (input2[pointer] != ':') {
                            pointer++;
                        }
                        int length = Integer.valueOf(new String(Arrays.copyOfRange(input2, 0, pointer)));
                        out.add(bdecode(Arrays.copyOfRange(input2, 0, length + pointer + 1)));
                        input2 = Arrays.copyOfRange(input2, length + pointer + 1, input2.length);
                    }
                    if (input2.length == 0) {
                        return out;
                    }
                }
            } else if (input2[0] == 'i') {
                return Integer.valueOf(new String(Arrays.copyOfRange(input2, 1, input2.length - 1)));

            } else if (input2[0] == 'd') {
                input2 = Arrays.copyOfRange(input2, 1, input2.length - 1);
                HashMap<String, Object> out = new HashMap<>();
                boolean running = true;
                while (running) {
                    int pointer = 1;
                    while (input2[pointer] != ':') {
                        pointer++;
                    }
                    int length = Integer.valueOf(new String(Arrays.copyOfRange(input2, 0, pointer)));
                    String id = new String(Arrays.copyOfRange(input2, 0, length + pointer + 1));
                    input2 = Arrays.copyOfRange(input2, length + pointer + 1, input2.length);
                    pointer = 1;
                    if (input2[0] == 'i' || input2[0] == 'd' || input2[0] == 'l') {
                        while (input2[pointer] != 'e') {
                            pointer++;
                        }
                        out.put(id, bdecode(Arrays.copyOfRange(input2, 0, pointer)));
                        input2 = Arrays.copyOfRange(input2, pointer + 1, input2.length);
                    } else {
                        while (input2[pointer] != ':') {
                            pointer++;
                        }
                        length = Integer.valueOf(new String(Arrays.copyOfRange(input2, 0, pointer)));
                        out.put(id, bdecode(Arrays.copyOfRange(input2, 0, length + pointer + 1)));
                        input2 = Arrays.copyOfRange(input2, length + pointer + 1, input2.length);
                    }
                    if (input2.length == 0) {
                        return out;
                    }
                }
            }
        }

        return null;

    }
}
