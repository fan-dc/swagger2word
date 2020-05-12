/**
 * 2020 fan.z
 */
package org.word.utils;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * <pre>
 * 类说明：
 * 
 * Modify Information:
 * Author         Date         Description
 * ============ =========== ============================
 * fan.z        2020-05-12    Create this file
 * </pre>
 */
public class YamlUtils {

    public static <T> T readValue(String yamlStr, Class<T> clazz) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper(new YAMLFactory());

        return objectMapper.readValue(yamlStr, clazz);
    }
}
