/**
 * Copyright 1999-2015 dangdang.com.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * </p>
 */

package com.dangdang.ddframe.rdb.sharding.config.yaml.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.Map;
import javax.sql.DataSource;

import com.dangdang.ddframe.rdb.sharding.api.ShardingDataSource;
import com.dangdang.ddframe.rdb.sharding.config.common.api.ShardingRuleBuilder;
import com.dangdang.ddframe.rdb.sharding.config.yaml.internel.YamlConfig;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * 基于配置文件的分片规则.
 *
 * @author gaohongtao
 */
public class YamlShardingDataSource extends ShardingDataSource {
    
    public YamlShardingDataSource(final File yamlFile) throws FileNotFoundException {
        super(new ShardingRuleBuilder().parse(yamlFile.getName(), parse(yamlFile)).build(), parse(yamlFile).getProps());
    }
    
    public YamlShardingDataSource(final Map<String, DataSource> dataSource, final File yamlFile) throws FileNotFoundException {
        super(new ShardingRuleBuilder().setDataSourceMap(dataSource).parse(yamlFile.getName(), parse(yamlFile)).build(), parse(yamlFile).getProps());
    }
    
    private static YamlConfig parse(final File yamlFile) throws FileNotFoundException {
        return (YamlConfig) new Yaml(new Constructor(YamlConfig.class)).load(new FileReader(yamlFile));
    }
}
