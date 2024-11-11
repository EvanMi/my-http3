package io.netty.incubator.codec.http3.example;

import static io.netty.buffer.Unpooled.unreleasableBuffer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Caches the images to avoid reading them every time from the disk.
 */
public final class ImageCache {
    private static final int BLOCK_SIZE = 1024;

    public static ImageCache INSTANCE = new ImageCache();

    private final Map<String, ByteBuf> imageBank = new HashMap<String, ByteBuf>(200);

    private ImageCache() {
        init();
    }

    public static String name(int x, int y) {
        return "tile-" + y + "-" + x + ".jpeg";
    }

    public ByteBuf image(int x, int y) {
        return imageBank.get(name(x, y));
    }

    private void init() {
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 20; x++) {
                try {
                    String name = name(x, y);
                    ByteBuf fileBytes = unreleasableBuffer(toByteBuf(getClass()
                            .getResourceAsStream(name)).asReadOnly());
                    imageBank.put(name, fileBytes);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static ByteBuf toByteBuf(InputStream input) throws IOException {
        ByteBuf buf = Unpooled.buffer();
        int n = 0;
        do {
            n = buf.writeBytes(input, BLOCK_SIZE);
        } while (n > 0);
        return buf;
    }
}

