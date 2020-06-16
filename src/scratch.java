import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class scratch {
  public static void main(String[] args) {
    System.out.println((byte)-1 & 0xff);
  }

  private static int networkInt(int a, int b, int c, int d) {
    return (d & 0xff) | ((c & 0xff) << 8) | ((b & 0xff) << 16) | ((a & 0xff) << 24);
  }
}
