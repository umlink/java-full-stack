package com.example.bootserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Spring MVC Servlet 外部挂载路径的单一配置读取入口。
 *
 * {@code spring.mvc.servlet.path} 决定 DispatcherServlet 的外部地址；Controller 与 Spring Security
 * 只处理内部资源路径。真实 HTTP 测试通过本类组合外部地址，修改前缀时不需要逐个改写 URL。
 */
@ConfigurationProperties(prefix = ServletPathProperties.BINDING_PREFIX)
public class ServletPathProperties {

    /** Spring Boot 配置绑定前缀。 */
    public static final String BINDING_PREFIX = "spring.mvc.servlet";

    /** MVC Servlet 外部路径的完整配置键，供测试覆盖配置时复用。 */
    public static final String PATH_PROPERTY = BINDING_PREFIX + ".path";

    private static final String ROOT_PATH = "/";

    private String path;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = normalizeServletPath(path);
    }

    /**
     * 将内部资源路径转换为真实 HTTP 外部路径。
     *
     * 配置值允许省略前导斜杠或带尾随斜杠；绑定时统一为 {@code /gateway} 形式，空值和根路径则
     * 表示不额外挂载前缀。资源路径同样会规范为单个前导斜杠，避免测试 URL 出现 {@code //}。
     */
    public String toExternalPath(String resourcePath) {
        String normalizedPath = normalizeServletPath(path);
        String normalizedResourcePath = normalizeResourcePath(resourcePath);
        return normalizedPath.isEmpty() ? normalizedResourcePath : normalizedPath + normalizedResourcePath;
    }

    private static String normalizeServletPath(String configuredPath) {
        if (configuredPath == null || configuredPath.isBlank()) {
            return "";
        }
        String pathWithoutBoundarySlashes = configuredPath.trim().replaceAll("^/+|/+$", "");
        return pathWithoutBoundarySlashes.isEmpty() ? "" : ROOT_PATH + pathWithoutBoundarySlashes;
    }

    private static String normalizeResourcePath(String resourcePath) {
        if (resourcePath == null || resourcePath.isBlank()) {
            return ROOT_PATH;
        }
        return ROOT_PATH + resourcePath.trim().replaceFirst("^/+", "");
    }
}
