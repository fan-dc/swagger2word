package org.word.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestTemplate;
import org.word.model.Request;
import org.word.model.Response;
import org.word.model.ResponseModelAttr;
import org.word.model.Table;
import org.word.service.WordService;
import org.word.utils.JsonUtils;
import org.word.utils.YamlUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;

/**
 * @Author XiuYin.Cui
 * @Date 2018/1/12
 **/
@SuppressWarnings({"unchecked", "rawtypes"})
@Slf4j
@Service("wordServiceForV3Impl")
public class WordServiceForV3Impl implements WordService {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${swagger.url}")
    private String swaggerUrl;

	@Override
    public Map<String, Object> tableList(String configUrl, String fmt) {
	    configUrl = StringUtils.defaultIfBlank(configUrl, swaggerUrl);
        
        Map<String, Object> resultMap = new HashMap<>();
        List<Table> result = new ArrayList<>();
        try {
            String configStr = restTemplate.getForObject(configUrl, String.class);
            Map<String, Object> map;
            if ("yaml".equalsIgnoreCase(fmt)) {
                map = YamlUtils.readValue(configStr, HashMap.class);
            } else {
                // convert JSON string to Map
                map = JsonUtils.readValue(configStr, HashMap.class);
            }
            
            //解析model
            Map<String, Object> definitinMap = parseDefinitions(map);
            
            //解析paths
            Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) map.get("paths");
            if (paths != null) {
                Iterator<Map.Entry<String, Map<String, Object>>> it = paths.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Map<String, Object>> path = it.next();

                    Iterator<Map.Entry<String, Object>> pathMethod = path.getValue().entrySet().iterator();
                    // 1.请求路径
                    String url = path.getKey();
                    // 遍历请求方法
                    while (pathMethod.hasNext()) {
                        // 3. 解析请求方式
                        Map.Entry<String, Object> request = pathMethod.next();
                        
                        // 2.请求方式，类似为 get,post,delete,put 这样
                        //String requestType = StringUtils.join(path.getValue().keySet(), ",");
                        String requestType = request.getKey();
                        // 跳过parameters配置
                        if ("parameters".equalsIgnoreCase(requestType)) {
                            continue;
                        }
                        //封装Table
                        Table table = new Table();
                        
                        table.setUrl(url);
                        //table.setResponseForm(responseForm);
                        table.setRequestType(requestType);
                        
                        // 解析请求内容
                        Map<String, Object> content = (Map<String, Object>)request.getValue();

                        // 4. 大标题（类说明）
                        String title = String.valueOf(((List) content.get("tags")).get(0));
                        table.setTitle(title);

                        
                        // 5.小标题 （方法说明）
                        String tag = String.valueOf(content.get("summary"));
                        table.setTag(tag);

                        
                        // 6.接口描述
                        String description = String.valueOf(content.get("summary"));
                        table.setDescription(description);

                        
                        // 请求参数
                        // 7.1 parameters
                        List<LinkedHashMap> parameters = new ArrayList<>();
                        if (path.getValue().get("parameters") != null) {
                            parameters.addAll((ArrayList) path.getValue().get("parameters"));
                        }
                        if (content.get("parameters") != null) {
                            parameters.addAll((ArrayList) content.get("parameters"));
                        }
                        table.setRequestList(processRequestList(parameters));
                        // 7.2 body
                        //String requestForm = "";
                        Map<String, Object> requestBody = (Map<String, Object>) content.get("requestBody");
                        if (requestBody != null && requestBody.get("content") != null) {
                            for (Map.Entry<String, Object> entry : ((Map<String, Object>) requestBody.get("content")).entrySet()) {
                                table.setRequestForm(entry.getKey());
                                table.setRequestModeAttrList(processResponseModelAttrs((Map<String, Object>)entry.getValue(), definitinMap));
                            }
                        }
                        /*// 7.请求参数格式，类似于 multipart/form-data
                        if (content.containsKey("consumes")) {
                            List<String> consumes = (List) content.get("consumes");
                            if (consumes != null && consumes.size() > 0) {
                                requestForm = StringUtils.join(consumes, ",");
                            }
                        }*/
                        
                        // 8.返回参数格式，类似于 application/json
                        /*String responseForm = "";
                        if (content.containsKey("produces")) {
                            List<String> produces = (List) content.get("produces");
                            if (produces != null && produces.size() > 0) {
                                responseForm = StringUtils.join(produces, ",");
                            }
                        }*/
                        
                        // 9. 请求体
                        
                        
                        // 10.返回体
                        Map<String, Object> responses = (LinkedHashMap) content.get("responses");
    
                        table.setResponseList(processResponseCodeList(responses));
                        
                        // 取出来状态是200时的返回值
                        Map<String, Map<String, Object>> obj = (Map)responses.entrySet().iterator().next().getValue();//.get("200");
                        if (obj != null && obj.get("content")!=null) {
                            table.setResponseForm(StringUtils.join(obj.get("content").keySet(), ","));
                            for (Map.Entry<String, Object> entry : obj.get("content").entrySet()) {
                                table.setResponseModeAttrList(processResponseModelAttrs((Map<String, Object>)entry.getValue(), definitinMap));
                                //示例
                                table.setRequestParam(JsonUtils.writeJsonStr(buildParamMap(table.getRequestList(), map)));
                                table.setResponseParam(processResponseParam((Map<String, Object>)entry.getValue(), map));
                            }
                        }
                        
                        
                        result.add(table);
                    }

                }
                
                //排序，同类别的接口归并在一起
                Collections.sort(result, new Comparator<Table>() {
                	public int compare(Table o1, Table o2) {
                		return o1.getTitle().compareTo(o2.getTitle());
                	};
                });
            }
            resultMap.put("serial", this.getSerial(result));
            resultMap.put("tables", result);
            resultMap.put("info", map.get("info"));
            
            log.debug(JsonUtils.writeJsonStr(resultMap));
        } catch (Exception e) {
            log.error("parse error", e);
        }
        return resultMap;
    }
	
	/**
     * <pre>
     * 方法说明：
     * </pre>
     * @param result
     * @return
     */
    private List<String> getSerial(List<Table> result) {
        if (result == null) {
            return null;
        }
        List<String> list = new ArrayList<>();
        String preTitle = "";
        int first = 0;
        int second = 1;
        for (Table table : result) {
            if (!preTitle.equals(table.getTitle())) {
                first++;
                second = 1;
                list.add(first+"");
            } else {
                list.add(first + "-" + second);
            }
            
            second++;
            preTitle = table.getTitle();
        }
        return list;
    }

    /**
	 * 处理请求参数列表
	 * @param parameters
	 * @return
	 */
	private List<Request> processRequestList(List<LinkedHashMap> parameters){
		List<Request> requestList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(parameters)) {
            for (Map<String, Object> param : parameters) {
                Request request = new Request();
                request.setName(String.valueOf(param.get("name")));
                Object in = param.get("in");
                if (in != null && "body".equals(in)) {
                    request.setType(String.valueOf(in));
                    Map<String, Object> schema = (Map) param.get("schema");
                    Object ref = schema.get("$ref");
                    // 数组情况另外处理
                    if (schema.get("type") != null && "array".equals(schema.get("type"))) {
                        ref = ((Map) schema.get("items")).get("$ref");
                    }
                    request.setParamType(ref == null ? "{}" : ref.toString());
                } else {
                    request.setType(param.get("type") == null ? "Object" : param.get("type").toString());
                    request.setParamType(String.valueOf(in));
                }
                if (param.get("required") != null) {
                    request.setRequire((Boolean) param.get("required"));
                } else {
                    request.setRequire(false);
                }
                request.setRemark(String.valueOf(param.get("description")));
                request.setParamType(request.getParamType().replaceAll("#/components/schemas/", ""));
                requestList.add(request);
            }
        }
        return requestList;
	}
	
    
	/**
	 * 处理返回码列表
	 * @param responses
	 * @return
	 */
	private List<Response> processResponseCodeList(Map<String, Object> responses){
		List<Response> responseList = new ArrayList<>();
        Iterator<Map.Entry<String, Object>> it3 = responses.entrySet().iterator();
        while (it3.hasNext()) {
            Response response = new Response();
            Map.Entry<String, Object> entry = it3.next();
            // 状态码 200 201 401 403 404 这样
            response.setName(entry.getKey());
            LinkedHashMap<String, Object> statusCodeInfo = (LinkedHashMap) entry.getValue();
            response.setDescription(String.valueOf(statusCodeInfo.get("description")));
//            response.setRemark(String.valueOf(statusCodeInfo.get("description")));
            responseList.add(response);
        }
		return responseList;
	}
	
	/**
	 * 处理返回属性列表
	 * @param responseObj
	 * @param definitinMap
	 * @return
	 */
    private List<ResponseModelAttr> processResponseModelAttrs(Map<String, Object> responseObj, Map<String, Object> definitinMap){
    	List<ResponseModelAttr> attrList=new ArrayList<>();
        Map<String, Object> schema = (Map<String, Object>)responseObj.get("schema");
        String type=(String)schema.get("type");
        String ref = null;
        if("array".equals(type)) {//数组
        	Map<String, Object> items = (Map<String, Object>)schema.get("items");
            if (items != null && items.get("$ref") != null) {
                ref = (String) items.get("$ref");
            }
        }else {
        	if (schema.get("$ref") != null) {//对象
                ref = (String)schema.get("$ref");
            }else {//其他类型
            	ResponseModelAttr attr=new ResponseModelAttr();
            	attr.setType(type);
            	attrList.add(attr);
            }
        }
        
        if(StringUtils.isNotBlank(ref)) {
        	Map<String, Object> mode = (Map<String,Object>)definitinMap.get(ref);
        	if (mode != null) {
                ResponseModelAttr attr=new ResponseModelAttr();
                attr.setClassName(ref);
                attr.setName((String)mode.get("description"));
                attr.setType(StringUtils.defaultIfBlank(type, StringUtils.EMPTY));
                attrList.add(attr);
                
                attrList.addAll((List<ResponseModelAttr>)mode.get("properties"));
            } else {
                ResponseModelAttr attr=new ResponseModelAttr();
                attr.setName(ref);
                attr.setType(StringUtils.defaultIfBlank(type, StringUtils.EMPTY));
                attrList.add(attr);
            }
        	
        }
    	return attrList;
    }
    
    /**
     * 解析Definition
     * @param map
     * @return
     */
    private Map<String, Object> parseDefinitions(Map<String, Object> map){
        Map<String, Object> components = (Map<String, Object>) map.get("components");
    	Map<String, Map<String, Object>> definitions = (Map<String, Map<String, Object>>) components.get("schemas");
        Map<String, Object> definitinMap = new HashMap<String, Object>();
        if(definitions!=null) {
        	Iterator<String> modelNameIt=definitions.keySet().iterator();
        	
        	String modeName = null;
        	Entry<String, Object> pEntry=null;
        	ResponseModelAttr modeAttr=null;
        	Map<String, Object> attrInfoMap=null;
        	while (modelNameIt.hasNext()) {
				modeName = modelNameIt.next();
				Map<String, Object> modeProperties=(Map<String, Object>)definitions.get(modeName).get("properties");
				if (modeProperties == null || modeProperties.isEmpty()) {
                    continue;
                }
				Iterator<Entry<String, Object>> pIt= modeProperties.entrySet().iterator();
				
				List<ResponseModelAttr> attrList=new ArrayList<>();
				
				//解析属性
				while (pIt.hasNext()) {
					pEntry=pIt.next();
					modeAttr=new ResponseModelAttr();
					modeAttr.setValue(pEntry.getKey());
					attrInfoMap=(Map<String, Object>)pEntry.getValue();
					modeAttr.setName((String)attrInfoMap.get("description"));
					modeAttr.setType((String)attrInfoMap.get("type"));
					if(attrInfoMap.get("format")!=null) {
						modeAttr.setType(modeAttr.getType()+"("+(String)attrInfoMap.get("format")+")");
					}
					attrList.add(modeAttr);
				}
				
				Map<String, Object> mode=new HashMap<>();
				mode.put("title", definitions.get(modeName).get("title"));
				mode.put("description", definitions.get(modeName).get("description"));
				mode.put("properties", attrList);
				
				definitinMap.put("#/components/schemas/"+modeName, mode);
			}
        }
        return definitinMap;
    }

    /**
     * 处理返回值
     * @param responseObj
     * @param map
     * @return
     */
    private String processResponseParam(Map<String, Object> responseObj, Map<String, Object> map){
    	if (responseObj != null && responseObj.get("schema")!=null) {
	        Map<String, Object> schema = (Map<String, Object>)responseObj.get("schema");
	        String type=(String)schema.get("type");
	        String ref = null;
	        if("array".equals(type)) {//数组
	        	Map<String, Object> items = (Map<String, Object>)schema.get("items");
	            if (items != null && items.get("$ref") != null) {
	                ref = (String) items.get("$ref");
	                
	                ObjectNode objectNode = parseRef(ref, map);
	                ArrayNode arrayNode = JsonUtils.createArrayNode();
	                arrayNode.add(objectNode);
	                return arrayNode.toString();
	            }
	        }else if (schema.get("$ref") != null) {
	            ref = (String)schema.get("$ref");
	            
	            ObjectNode objectNode = parseRef(ref, map);
	            return objectNode.toString();
	        }
        }
        
    	return StringUtils.EMPTY;
    }
    

    /**
     * 从map中解析出指定的ref
     *
     * @param ref ref链接 例如："#/definitions/PageInfoBT«Customer»"
     * @param map 是整个swagger json转成map对象
     * @return
     * @author fpzhan
     */
    private ObjectNode parseRef(String ref, Map<String, Object> map) {
        ObjectNode objectNode = JsonUtils.createObjectNode();
        if (StringUtils.isNotEmpty(ref) && ref.startsWith("#")) {
            String[] refs = ref.split("/");
            Map<String, Object> tmpMap = map;
            //取出ref最后一个参数 start
            for (String tmp : refs) {
                if (!"#".equals(tmp)) {
                    tmpMap = (Map<String, Object>) tmpMap.get(tmp);
                }
            }
            //取出ref最后一个参数 end
            //取出参数
            if (tmpMap == null) {
                return objectNode;
            }
            Object properties = tmpMap.get("properties");
            if (properties == null) {
                return objectNode;
            }
            Map<String, Object> propertiesMap = (Map<String, Object>) properties;
            Set<String> keys = propertiesMap.keySet();
            //遍历key
            for (String key : keys) {
                Map<String, Object> keyMap = (Map) propertiesMap.get(key);
                if ("array".equals(keyMap.get("type"))) {
                    //数组的处理方式
                    String sonRef = (String) ((Map) keyMap.get("items")).get("$ref");
                    //对象自包含，跳过解析
                    if (ref.equals(sonRef)) {
                        continue;
                    }
                    JsonNode jsonNode = parseRef(sonRef, map);
                    ArrayNode arrayNode = JsonUtils.createArrayNode();
                    arrayNode.add(jsonNode);
                    objectNode.set(key, arrayNode);
                } else if (keyMap.get("$ref") != null) {
                    //对象的处理方式
                    String sonRef = (String) keyMap.get("$ref");
                    //对象自包含，跳过解析
                    if (ref.equals(sonRef)) {
                        continue;
                    }
                    ObjectNode object = parseRef(sonRef, map);
                    objectNode.set(key, object);
                } else {
                    //其他参数的处理方式，string、int
                    String str = "";
                    if (keyMap.get("description") != null) {
                        str = str + keyMap.get("description");
                    }
                    if (keyMap.get("format") != null) {
                        str = str + String.format("格式为(%s)", keyMap.get("format"));
                    }
                    objectNode.put(key, str);
                }
            }
        }
        return objectNode;
    }

    /**
     * 封装post请求体
     *
     * @param list
     * @param map
     * @return
     */
    private Map<String, Object> buildParamMap(List<Request> list, Map<String, Object> map) throws IOException {
        Map<String, Object> paramMap = new HashMap<>(8);
        if (list != null && list.size() > 0) {
            for (Request request : list) {
                String name = request.getName();
                String type = request.getType();
                switch (type) {
                    case "string":
                        paramMap.put(name, "string");
                        break;
                    case "integer":
                        paramMap.put(name, 0);
                        break;
                    case "number":
                        paramMap.put(name, 0.0);
                        break;
                    case "boolean":
                        paramMap.put(name, true);
                        break;
                    case "body":
                        String paramType = request.getParamType();
                        ObjectNode objectNode = parseRef(paramType, map);
                        paramMap = JsonUtils.readValue(objectNode.toString(), Map.class);
                        break;
                    default:
                        paramMap.put(name, null);
                        break;
                }
            }
        }
        return paramMap;
    }
}
