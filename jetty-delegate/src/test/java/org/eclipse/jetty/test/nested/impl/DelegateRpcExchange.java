//
// ========================================================================
// Copyright (c) 1995-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.test.nested.impl;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.delegate.api.DelegateExchange;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.io.ByteBufferAccumulator;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.test.nested.rpc.MockRpcRequest;
import org.eclipse.jetty.test.nested.rpc.MockRpcResponse;
import org.eclipse.jetty.util.Callback;

public class DelegateRpcExchange implements DelegateExchange
{
    private static final Content.Chunk EOF = Content.Chunk.EOF;
    private final MockRpcRequest _request;
    private final AtomicReference<Content.Chunk> _content = new AtomicReference<>();
    private final MockRpcResponse _response;
    private final ByteBufferAccumulator accumulator = new ByteBufferAccumulator();
    private final CompletableFuture<Void> _completion = new CompletableFuture<>();

    public DelegateRpcExchange(MockRpcRequest request, MockRpcResponse response)
    {
        _request = request;
        _response = response;
        _content.set(new ContentChunk(request.getData()));
    }

    @Override
    public String getRequestURI()
    {
        return _request.getUrl();
    }

    @Override
    public String getProtocol()
    {
        return _request.getHttpVersion();
    }

    @Override
    public String getMethod()
    {
        return _request.getMethod();
    }

    @Override
    public HttpFields getHeaders()
    {
        return _request.getHeadersList();
    }

    @Override
    public InetSocketAddress getRemoteAddr()
    {
        return InetSocketAddress.createUnresolved(_request.getUserIp(), 0);
    }

    @Override
    public InetSocketAddress getLocalAddr()
    {
        return InetSocketAddress.createUnresolved("0.0.0.0", 0);
    }

    @Override
    public Content.Chunk read()
    {
        return _content.getAndUpdate(chunk -> (chunk instanceof ContentChunk) ? EOF : chunk);
    }

    @Override
    public void demand(Runnable demandCallback)
    {
        demandCallback.run();
    }

    @Override
    public void fail(Throwable failure)
    {
        _content.set(Content.Chunk.from(failure));
    }

    @Override
    public void setStatus(int status)
    {
        _response.setHttpResponseCode(status);
    }

    @Override
    public void addHeader(String name, String value)
    {
        _response.addHttpOutputHeaders(name, value);
    }

    @Override
    public void write(boolean last, ByteBuffer content, Callback callback)
    {
        accumulator.copyBuffer(content);
        callback.succeeded();
    }

    @Override
    public void succeeded()
    {
        _response.setHttpResponseResponse(accumulator.toByteBuffer());
        _completion.complete(null);
    }

    @Override
    public void failed(Throwable x)
    {
        _completion.completeExceptionally(x);
    }

    public void awaitResponse() throws ExecutionException, InterruptedException
    {
        _completion.get();
    }
}
