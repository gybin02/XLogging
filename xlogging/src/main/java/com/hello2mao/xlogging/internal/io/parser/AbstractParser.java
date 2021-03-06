package com.hello2mao.xlogging.internal.io.parser;

import com.hello2mao.xlogging.internal.io.CharBuffer;
import com.hello2mao.xlogging.internal.log.XLog;
import com.hello2mao.xlogging.internal.log.XLogManager;

import junit.framework.Assert;

public abstract class AbstractParser {

    protected static final XLog log = XLogManager.getAgentLog();
    protected CharBuffer buffer;
    protected int charactersInMessage;
    protected long currentTimeStamp;
    private HttpParserHandler handler;
    private int maxBufferSize;

    public AbstractParser(AbstractParser parser) {
        initialize(parser.handler, parser.charactersInMessage);
    }

    public AbstractParser(HttpParserHandler httpParserHandler) {
        initialize(httpParserHandler, 0);
    }

    private void initialize(HttpParserHandler handler, int charactersInMessage) {
        this.handler = handler;
        this.maxBufferSize = getMaxBufferSize();
        this.buffer = new CharBuffer(getInitialBufferSize());
        this.charactersInMessage = charactersInMessage;
    }

    /**
     * 对缓存的buffer进行解析
     * @param charBuffer charBuffer
     * @return boolean
     */
    public abstract boolean parse(CharBuffer charBuffer);

    /**
     * 把单个字节加入缓存buffer进行parser
     *
     * @param oneByte the next byte of data from the stream.
     *             The value byte is an <code>int</code> in the range <code>0</code> to <code>255</code>.
     *             Or <code>-1</code> if the end of the stream is reached.
     * @return boolean
     */
    public boolean add(int oneByte) {
        if (oneByte == -1) { // -1表示流结束了
            log.warning("AbstractParser: add oneByte reachedEOF");
            reachedEOF();
            return true;
        }
        this.charactersInMessage += 1;
        char character = (char) oneByte;
        AbstractParser parser;
        if (character == '\n') { // 遇到换行符就进行解析，会调用相应parser，即一行一解析
            if (parse(buffer)) { // 对缓冲的buffer进行解析
                // 解析成功，则获取下个parser
                parser = nextParserAfterSuccessfulParse();
            } else {
                // 解析失败，则设为Noop
                parser = NoopLineParser.DEFAULT;
            }
        } else if (buffer.length < maxBufferSize) { // 不是换行符，且buffer大小在max内，则缓存在buffer
            int targetLength = buffer.length + 1;
            // buffer空间不够，则double空间大小
            if (targetLength > buffer.charArray.length) {
                final char[] dest = new char[Math.max(buffer.charArray.length << 1, targetLength)];
                System.arraycopy(buffer.charArray, 0, dest, 0, buffer.length);
                buffer.charArray = dest;
            }
            buffer.charArray[buffer.length] = character;
            buffer.length = targetLength;
            parser = this;
        } else { // buffer满
            parser = nextParserAfterBufferFull();
        }
        Assert.assertNotNull(parser);
        if (parser != this) {
            handler.setNextParser(parser);
        }
        return parser != this;
    }

    /**
     * 把内容块加入缓存buffer进行parser
     *
     * @param buffer byte[]
     * @param offset int
     * @param count int
     */
    public void add(byte[] buffer, int offset, int count) {
        int j;
        for (int i = addBlock(buffer, offset, count); i > 0 && i < count; i += j) {
            j = handler.getCurrentParser().addBlock(buffer, offset + i, count - i);
            if (j <= 0) {
                break;
            }
        }
    }

    protected int addBlock(byte[] buffer, int offset, int count) {
        if (count == -1) {
            reachedEOF();
            return -1;
        }
        if (buffer == null || count == 0) {
            return -1;
        }
        boolean bool = false;
        int i = 0;
        while (!bool && i < count) {
            bool = add((char) buffer[offset + i]);
            ++i;
        }
        return i;
    }

    protected int getCharactersInMessage() {
        return charactersInMessage;
    }

    public HttpParserHandler getHandler() {
        return handler;
    }

    protected abstract int getInitialBufferSize();

    protected abstract int getMaxBufferSize();

    public abstract AbstractParser nextParserAfterBufferFull();

    public abstract AbstractParser nextParserAfterSuccessfulParse();

    private void reachedEOF() {
        getHandler().setNextParser(NoopLineParser.DEFAULT);
    }

    protected void setCharactersInMessage(int charactersInMessage) {
        this.charactersInMessage = charactersInMessage;
    }

    public String toString() {
        return buffer.toString();
    }

    public void close() {
        if (handler != null) {
            handler.setNextParser(NoopLineParser.DEFAULT);
        }
    }
}
