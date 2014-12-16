package com.mule.module.bufferaggregator;

public class BufferException extends Exception
{
    private static final long serialVersionUID = -3059972060538322495L;

    public BufferException()
    {
        super();
    }

    public BufferException(String message)
    {
        super(message);
    }

    public BufferException(String message, Throwable e)
    {
        super(message, e);
    }
}
