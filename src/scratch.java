import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class scratch {
  public static void main(String[] args) {
    ByteBuffer buf = ByteBuffer.allocate(17);
    buf.putInt(13).put((byte)6).putInt(15).putInt(10).putInt(16384);
    System.out.println(Arrays.toString(buf.array()));
  }

  private static int networkInt(int a, int b, int c, int d) {
    return (d & 0xff) | ((c & 0xff) << 8) | ((b & 0xff) << 16) | ((a & 0xff) << 24);
  }
}
