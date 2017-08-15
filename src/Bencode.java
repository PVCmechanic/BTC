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

        }else{
            input2 = Arrays.copyOfRange(input2,1,input2.length-1);
            System.out.println(input2.length);
            if(input2[0] == 'l'){
                List out = new ArrayList();
                boolean running = true;
                while(running){
                    int p = 1;
                    if(input2[0] == 'i' || input2[0] == 'd' || input2[0] == 'l'){
                        while(input2[p++] != 'e');
                        out.add(bdecode(Arrays.copyOfRange(input2,0,p)));
                        input2 = Arrays.copyOfRange(input2,p,input2.length);
                    }else{
                        while(input2[p++] != ':');
                        int e = Integer.valueOf(new String(Arrays.copyOfRange(input2,0, p)));
                        out.add(bdecode(Arrays.copyOfRange(input2,0,e)));
                    }
                    if(p==input2.length){
                        running = false;
                    }

                }
                return out;
            }else if(input2[0] == 'i'){

            }else if(input2[0] == 'd'){

            }
        }

        return null;

    }
}
