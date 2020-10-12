/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.shardingsphere.scaling.web;

import com.google.gson.Gson;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;
import lombok.SneakyThrows;
import org.apache.shardingsphere.scaling.core.config.ScalingConfiguration;
import org.apache.shardingsphere.scaling.core.config.ScalingContext;
import org.apache.shardingsphere.scaling.core.config.ScalingDataSourceConfiguration;
import org.apache.shardingsphere.scaling.core.config.ServerConfiguration;
import org.apache.shardingsphere.scaling.core.config.SyncConfiguration;
import org.apache.shardingsphere.scaling.core.datasource.DataSourceManager;
import org.apache.shardingsphere.scaling.core.utils.SyncConfigurationUtil;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import javax.sql.DataSource;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public final class HttpServerHandlerTest {
    
    private static final Gson GSON = new Gson();
    
    @Mock
    private ChannelHandlerContext channelHandlerContext;
    
    private FullHttpRequest fullHttpRequest;
    
    private HttpServerHandler httpServerHandler;
    
    private ScalingConfiguration scalingConfig;
    
    private SyncConfiguration syncConfiguration;
    
    @Before
    public void setUp() {
        initConfig("/config.json");
        ScalingContext.getInstance().init(new ServerConfiguration());
        httpServerHandler = new HttpServerHandler();
        initTableData(syncConfiguration.getDumperConfiguration().getDataSourceConfiguration());
        initTableData(syncConfiguration.getImporterConfiguration().getDataSourceConfiguration());
    }
    
    @Test
    public void assertChannelReadStartSuccess() {
        startScalingJob();
        ArgumentCaptor<FullHttpResponse> argumentCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(channelHandlerContext).writeAndFlush(argumentCaptor.capture());
        FullHttpResponse fullHttpResponse = argumentCaptor.getValue();
        assertTrue(fullHttpResponse.content().toString(CharsetUtil.UTF_8).contains("{\"success\":true"));
    }
    
    @Test
    public void assertShardingSphereJDBCTargetChannelReadStartSuccess() {
        initConfig("/config_sharding_sphere_jdbc_target.json");
        startScalingJob();
        ArgumentCaptor<FullHttpResponse> argumentCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(channelHandlerContext).writeAndFlush(argumentCaptor.capture());
        FullHttpResponse fullHttpResponse = argumentCaptor.getValue();
        assertTrue(fullHttpResponse.content().toString(CharsetUtil.UTF_8).contains("{\"success\":true"));
    }
    
    @Test
    public void assertChannelReadProgressFail() {
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/scaling/job/progress/9");
        httpServerHandler.channelRead0(channelHandlerContext, fullHttpRequest);
        ArgumentCaptor<FullHttpResponse> argumentCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(channelHandlerContext).writeAndFlush(argumentCaptor.capture());
        FullHttpResponse fullHttpResponse = argumentCaptor.getValue();
        assertTrue(fullHttpResponse.content().toString(CharsetUtil.UTF_8).contains("Can't find scaling job id 9"));
    }
    
    @Test
    public void assertChannelReadProgressSuccess() {
        startScalingJob();
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/scaling/job/progress/1");
        httpServerHandler.channelRead0(channelHandlerContext, fullHttpRequest);
        ArgumentCaptor<FullHttpResponse> argumentCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(channelHandlerContext, times(2)).writeAndFlush(argumentCaptor.capture());
        FullHttpResponse fullHttpResponse = argumentCaptor.getValue();
        assertTrue(fullHttpResponse.content().toString(CharsetUtil.UTF_8).contains("{\"success\":true"));
    }
    
    @Test
    public void assertChannelReadStop() {
        Map<String, Integer> map = new HashMap<>();
        map.put("id", 1);
        ByteBuf byteBuf = Unpooled.copiedBuffer(GSON.toJson(map), CharsetUtil.UTF_8);
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/scaling/job/stop", byteBuf);
        httpServerHandler.channelRead0(channelHandlerContext, fullHttpRequest);
        ArgumentCaptor<FullHttpResponse> argumentCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(channelHandlerContext).writeAndFlush(argumentCaptor.capture());
        FullHttpResponse fullHttpResponse = argumentCaptor.getValue();
        assertTrue(fullHttpResponse.content().toString(CharsetUtil.UTF_8).contains("{\"success\":true"));
    }
    
    @Test
    public void assertChannelReadList() {
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/scaling/job/list");
        httpServerHandler.channelRead0(channelHandlerContext, fullHttpRequest);
        ArgumentCaptor<FullHttpResponse> argumentCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(channelHandlerContext).writeAndFlush(argumentCaptor.capture());
        FullHttpResponse fullHttpResponse = argumentCaptor.getValue();
        assertTrue(fullHttpResponse.content().toString(CharsetUtil.UTF_8).contains("{\"success\":true"));
    }
    
    @Test
    public void assertChannelReadUnsupportedUrl() {
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.DELETE, "/scaling/1");
        httpServerHandler.channelRead0(channelHandlerContext, fullHttpRequest);
        ArgumentCaptor<FullHttpResponse> argumentCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(channelHandlerContext).writeAndFlush(argumentCaptor.capture());
        FullHttpResponse fullHttpResponse = argumentCaptor.getValue();
        assertTrue(fullHttpResponse.content().toString(CharsetUtil.UTF_8).contains("Not support request!"));
    }
    
    @Test
    public void assertChannelReadUnsupportedMethod() {
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.DELETE, "/scaling/job/stop");
        httpServerHandler.channelRead0(channelHandlerContext, fullHttpRequest);
        ArgumentCaptor<FullHttpResponse> argumentCaptor = ArgumentCaptor.forClass(FullHttpResponse.class);
        verify(channelHandlerContext).writeAndFlush(argumentCaptor.capture());
        FullHttpResponse fullHttpResponse = argumentCaptor.getValue();
        assertTrue(fullHttpResponse.content().toString(CharsetUtil.UTF_8).contains("Not support request!"));
    }
    
    @Test
    public void assertExceptionCaught() {
        Throwable throwable = mock(Throwable.class);
        httpServerHandler.exceptionCaught(channelHandlerContext, throwable);
        verify(channelHandlerContext).close();
    }

    private void startScalingJob() {
        ByteBuf byteBuf = Unpooled.copiedBuffer(GSON.toJson(scalingConfig), CharsetUtil.UTF_8);
        fullHttpRequest = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/scaling/job/start", byteBuf);
        httpServerHandler.channelRead0(channelHandlerContext, fullHttpRequest);
    }
    
    private void initConfig(final String configFile) {
        InputStream fileInputStream = HttpServerHandlerTest.class.getResourceAsStream(configFile);
        InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
        scalingConfig = GSON.fromJson(inputStreamReader, ScalingConfiguration.class);
        syncConfiguration = SyncConfigurationUtil.toSyncConfigurations(scalingConfig).iterator().next();
    }
    
    @SneakyThrows(SQLException.class)
    private void initTableData(final ScalingDataSourceConfiguration dataSourceConfig) {
        DataSource dataSource = new DataSourceManager().getDataSource(dataSourceConfig);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t1");
            statement.execute("CREATE TABLE t1 (id INT PRIMARY KEY, user_id VARCHAR(12))");
            statement.execute("DROP TABLE IF EXISTS t2");
            statement.execute("CREATE TABLE t2 (id INT PRIMARY KEY, user_id VARCHAR(12))");
        }
    }
}
