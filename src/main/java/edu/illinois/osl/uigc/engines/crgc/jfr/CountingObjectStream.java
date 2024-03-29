package edu.illinois.osl.uigc.engines.crgc.jfr;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;

/**
 * An ObjectOutputStream that records how many bytes have been written to it.
 */
public class CountingObjectStream extends OutputStream {
    private long bytesWritten = 0;
    private final ObjectOutputStream outputStream;

    public CountingObjectStream(ObjectOutputStream outputStream) {
        this.outputStream = outputStream;
    }

    @Override
    public void write(int b) throws IOException {
        outputStream.write(b);
        bytesWritten++;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        outputStream.write(b, off, len);
        bytesWritten += len;
    }

    public long getBytesWritten() {
        return bytesWritten;
    }
}