import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class Bencode {
    public Bencode() {}

    public byte[] bencode(Object input){
        if(input instanceof Integer){
            byte[] num = input.toString().getBytes();
            byte[] out = new byte[num.length + 2];
            out[0]='i';
            for(int i=0;i<num.length;i++){
                out[i+1]=num[i];
            }
            out[out.length-1]='e';


        }
    }

    public Object bdecode(byte[] input) throws IOException {
        if (input[0] >= '0' && input[0] <= '9') {
            int pointer = 1;
            while (input[pointer] != ':') {
                pointer++;
            }
            int length = Integer.valueOf(new String(Arrays.copyOfRange(input, 0, pointer)));
            return Arrays.copyOfRange(input, pointer + 1, pointer + length + 1);
        } else {
            if (input[0] == 'l' || input[0] == 'd') {
                boolean list = input[0] == 'l';
                input = Arrays.copyOfRange(input, 1, input.length - 1);
                List<Object> out = new ArrayList<>();
                while (true) {
                    int pointer = 1;
                    if (input[0] == 'i') {
                        while (input[pointer] != 'e') {
                            pointer++;
                        }
                        out.add(bdecode(Arrays.copyOfRange(input, 0, pointer + 1)));
                        input = Arrays.copyOfRange(input, pointer + 1, input.length);
                    } else if (input[0] == 'd' || input[0] == 'l') {
                        int indent = 0;
                        boolean searching = true;
                        while (searching) {
                            if (input[pointer] == 'i' || input[pointer] == 'l' || input[pointer] == 'd') {
                                indent++;
                            } else if (input[pointer] == 'e') {
                                indent--;
                                if (indent == -1) {
                                    searching = false;
                                }
                            } else if (indent == 0) {
                                int newpoint = pointer;
                                while (input[newpoint] != ':') {
                                    newpoint++;
                                }
                                pointer = newpoint + Integer.valueOf(new String(Arrays.copyOfRange(input, pointer, newpoint)));
                            }
                            pointer++;
                        }
                        out.add(bdecode(Arrays.copyOfRange(input, 0, pointer)));
                        input = Arrays.copyOfRange(input, pointer, input.length);
                    } else {
                        while (input[pointer] != ':') {
                            pointer++;
                        }
                        int length = Integer.valueOf(new String(Arrays.copyOfRange(input, 0, pointer)));
                        out.add(bdecode(Arrays.copyOfRange(input, 0, length + pointer + 1)));
                        input = Arrays.copyOfRange(input, length + pointer + 1, input.length);
                    }
                    if (input.length == 0) {
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
            } else if (input[0] == 'i') {
                return Integer.valueOf(new String(Arrays.copyOfRange(input, 1, input.length - 1)));

            }
        }

        return null;

    }
}
