package io.netty.incubator.codec.http3.example;

import static io.netty.util.CharsetUtil.UTF_8;

import java.util.Random;

/**
 * Static and dynamically generated HTML for the example.
 */
public final class Html {

    public static final byte[] FOOTER = "</body></html>".getBytes(UTF_8);

    public static final byte[] HEADER = ("<!DOCTYPE html><html><head lang=\"en\"><title>HTTP/3 Example</title>"
            + "<style>body {background:#DDD;} div#netty { line-height:0;}</style>"
            + "<link rel=\"shortcut icon\" href=\"about:blank\">"
            + "<meta charset=\"UTF-8\"></head><body>A grid of 200 tiled images is shown below. Compare:"
            + "<p>[<a href='https://programmer-yumi.top?latency=0'>HTTP/3, 0 latency</a>] <br/>"
            + "[<a href='https://programmer-yumi.top?latency=30'>HTTP/3, 30ms latency</a>] <br/>"
            + "[<a href='https://programmer-yumi.top?latency=200'>HTTP/3, 200ms latency</a>] <br/>"
            + "[<a href='https://programmer-yumi.top?latency=1000'>HTTP/3, 1s latency</a>] <br/>").getBytes(UTF_8);

    private static final int IMAGES_X_AXIS = 20;

    private static final int IMAGES_Y_AXIS = 10;

    private Html() {
    }

    public static byte[] body(int latency) {
        int r = Math.abs(new Random().nextInt());
        // The string to be built contains 13192 fixed characters plus the variable latency and random cache-bust.
        int numberOfCharacters = 13192 + stringLength(latency) + stringLength(r);
        StringBuilder sb = new StringBuilder(numberOfCharacters).append("<div id=\"netty\">");
        for (int y = 0; y < IMAGES_Y_AXIS; y++) {
            for (int x = 0; x < IMAGES_X_AXIS; x++) {
                sb.append("<img width=30 height=29 src='/http3?x=")
                        .append(x)
                        .append("&y=").append(y)
                        .append("&cachebust=").append(r)
                        .append("&latency=").append(latency)
                        .append("'>");
            }
            sb.append("<br/>\r\n");
        }
        sb.append("</div>");
        return sb.toString().getBytes(UTF_8);
    }

    private static int stringLength(int value) {
        return Integer.toString(value).length() * IMAGES_X_AXIS * IMAGES_Y_AXIS;
    }
}
