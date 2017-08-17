import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.io.IOUtils;


public class Bencode {
    public Bencode(){}
    public Object bdecode(byte[] input) throws IOException {
        InputStream inputStream = new FileInputStream("C:\\Users\\Max\\OneDrive\\BitTorrentClient\\NPR_1_archive.torrent");

        byte[] input2 = IOUtils.toByteArray(inputStream);

        String in = new String(input2,"ISO-8859-1");
        System.out.println(input2.length);
        System.out.println(in);
        if(input2[0] >= '0' && input2[0] <='9'){
            int pointer = 1;
            while(input2[pointer] != ':'){
                pointer++;
            }
            int length = Integer.valueOf(new String(Arrays.copyOfRange(input2,0, pointer)));
            byte[] out = Arrays.copyOfRange(input2,pointer,pointer + length + 1);
            return out;
        }else{
            System.out.println(input2.length);
            if(input2[0] == 'l'){
                input2 = Arrays.copyOfRange(input2,1,input2.length-1);
                List out = new ArrayList();
                boolean running = true;
                while(running){
                    int pointer = 1;
                    if(input2[0] == 'i' || input2[0] == 'd' || input2[0] == 'l'){
                        while(input2[pointer]!='e'){
                            pointer++;
                        }
                        out.add(bdecode(Arrays.copyOfRange(input2,0,pointer)));
                        input2 = Arrays.copyOfRange(input2,pointer,input2.length);
                    }else{
                        while(input2[pointer] != ':'){
                            pointer++;
                        }
                        int length = Integer.valueOf(new String(Arrays.copyOfRange(input2,0, pointer)));
                        out.add(bdecode(Arrays.copyOfRange(input2,0,length + pointer + 1)));
                        input2 = Arrays.copyOfRange(input2,length + pointer + 1, input2.length);
                    }
                    if(input2.length == 0){
                        return out;
                    }
                }
            }else if(input2[0] == 'i'){

            }else if(input2[0] == 'd'){

            }
        }

        return null;

    }
}
