package com.composum.sling.clientlibs.handle;

import com.composum.sling.clientlibs.service.ClientlibProcessor;
import com.composum.sling.clientlibs.servlet.ClientlibServlet;
import com.composum.sling.core.ResourceHandle;
import com.composum.sling.core.util.LinkUtil;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;

import javax.jcr.RepositoryException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Clientlib {

    public enum Type {link, css, js}

    public static final String PROP_EXPANDED = "expanded";
    public static final String PROP_EMBED = "embed";
    public static final String PROP_REL = "rel";

    public class Link {

        public final String url;
        public final Map<String, String> properties;

        public Link(String url, String... props) {
            this.url = url;
            properties = new LinkedHashMap<>();
            for (int i = 0; i + 1 < props.length; i += 2) {
                if (props[i + 1] != null) {
                    properties.put(props[i], props[i + 1]);
                }
            }
        }
    }

    protected final SlingHttpServletRequest request;
    protected final ResourceResolver resolver;
    protected final ResourceHandle resource;
    protected final ResourceHandle definition;

    protected final String path;
    protected final Type type;

    private transient Calendar lastModified;
    private transient FileHandle file;

    public Clientlib(SlingHttpServletRequest request, String path, Type type) {
        this.request = request;
        this.resolver = request.getResourceResolver();
        this.path = path;
        this.type = type;
        this.resource = ResourceHandle.use(retrieveResource(path));
        this.definition = retrieveDefinition();
    }

    public ResourceResolver getResolver() {
        return resolver;
    }

    protected ResourceHandle retrieveDefinition() {
        String path = this.path + "/" + type;
        Resource resource = retrieveResource(path);
        return ResourceHandle.use(resource);
    }

    protected Resource retrieveResource(String path) {
        Resource resource = null;
        if (!path.startsWith("/")) {
            resource = resolver.getResource("/apps/" + path);
            if (resource == null) {
                resource = resolver.getResource("/libs/" + path);
            }
        } else {
            resource = resolver.getResource(path);
        }
        return resource;
    }

    public boolean isValid() {
        return resource.isResourceType(ClientlibServlet.TYPE_CLIENTLIB) && definition.isValid();
    }

    public String getPath(String path) {
        Resource resource = retrieveResource(path);
        return resource != null ? resource.getPath() : "";
    }

    public String getPath() {
        return path + "." + type;
    }

    public Type getType() {
        return type;
    }

    public Calendar getLastModified() {
        if (lastModified == null) {
            lastModified = getLastModified(definition, null);
        }
        return lastModified;
    }

    protected Calendar getLastModified(ResourceHandle resource, Calendar lastModified) {
        if (resource.isValid()) {
            if (isFile(resource)) {
                lastModified = getLastModified(new FileHandle(resource), lastModified);
            } else {
                String reference = resource.getProperty(PROP_EMBED, "");
                if (StringUtils.isNotBlank(reference)) {
                    Resource target = retrieveResource(reference);
                    if (target != null) {
                        if (target.isResourceType(ClientlibServlet.TYPE_CLIENTLIB)) {
                            Clientlib embedded = new Clientlib(request, reference, type);
                            lastModified = getLastModified(embedded.getLastModified(), lastModified);
                        } else {
                            lastModified = getLastModified(new FileHandle(target), lastModified);
                        }
                    }
                } else {
                    for (Resource child : resource.getChildren()) {
                        lastModified = getLastModified(ResourceHandle.use(child), lastModified);
                    }
                }
            }
        }
        return lastModified;
    }

    protected Calendar getLastModified(FileHandle file, Calendar lastModified) {
        return getLastModified(file.getLastModified(), lastModified);
    }

    protected Calendar getLastModified(Calendar value, Calendar lastModified) {
        if (lastModified == null || lastModified.before(value)) {
            lastModified = value;
        }
        return lastModified;
    }

    public List<Link> getLinks(boolean expanded, Map<String, String> properties) {
        expanded = definition.getProperty(PROP_EXPANDED, expanded);
        List<Link> links = new ArrayList<>();
        if (definition.isValid()) {
            if (expanded) {
                getLinks(definition, properties, links);
            } else {
                links.add(getLink(resource, resource, properties, type));
            }
        }
        return links;
    }

    protected void getLinks(ResourceHandle resource, Map<String, String> properties, List<Link> links) {
        if (resource.isValid()) {
            if (isFile(resource)) {
                FileHandle file = new FileHandle(resource);
                if (file.isValid()) {
                    ResourceHandle fileRes = file.getResource();
                    links.add(getLink(fileRes, fileRes, properties, null));
                }
            } else {
                String reference = resource.getProperty(PROP_EMBED, "");
                if (StringUtils.isNotBlank(reference)) {
                    Resource target = retrieveResource(reference);
                    if (target != null) {
                        if (target.isResourceType(ClientlibServlet.TYPE_CLIENTLIB)) {
                            Clientlib embedded = new Clientlib(request, reference, type);
                            embedded.getLinks(embedded.definition, properties, links);
                        } else {
                            FileHandle file = new FileHandle(target);
                            if (file.isValid()) {
                                links.add(getLink(resource, file.getResource(), properties, null));
                            }
                        }
                    }
                } else {
                    for (Resource child : resource.getChildren()) {
                        getLinks(ResourceHandle.use(child), properties, links);
                    }
                }
            }
        }
    }

    protected Link getLink(ResourceHandle reference, Resource target, Map<String, String> properties, Type type) {
        String url = target.getPath();
        if (type != null) {
            url += "." + type;
        }
        url = LinkUtil.getUrl(request, url);
        String rel = reference.getProperty(PROP_REL, properties.get(PROP_REL));
        return new Link(url, PROP_REL, rel);
    }

    public void processContent(OutputStream output, ClientlibProcessor processor, Map<String, Object> hints)
            throws IOException, RepositoryException {
        processContent(definition, output, processor, hints);
        output.close();
    }

    protected void processContent(ResourceHandle resource, OutputStream output,
                                  ClientlibProcessor processor, Map<String, Object> hints)
            throws RepositoryException, IOException {
        if (resource.isValid()) {
            if (isFile(resource)) {
                processFile(resource, output, processor, hints);
            } else {
                String reference = resource.getProperty(PROP_EMBED, "");
                if (StringUtils.isNotBlank(reference)) {
                    Resource target = retrieveResource(reference);
                    if (target != null) {
                        if (target.isResourceType(ClientlibServlet.TYPE_CLIENTLIB)) {
                            Clientlib embedded = new Clientlib(request, reference, type);
                            embedded.processContent(embedded.definition, output, processor, hints);
                        } else {
                            processFile(target, output, processor, hints);
                        }
                    }
                } else {
                    for (Resource child : resource.getChildren()) {
                        processContent(ResourceHandle.use(child), output, processor, hints);
                    }
                }
            }
        }
    }

    protected void processFile(Resource resource, OutputStream output,
                               ClientlibProcessor processor, Map<String, Object> hints)
            throws RepositoryException, IOException {
        FileHandle file = new FileHandle(resource);
        InputStream content = file.getStream();
        if (content != null) {
            if (processor != null) {
                content = processor.processContent(this, content, hints);
            }
            IOUtils.copy(content, output);
            output.write('\n');
            output.flush();
        }
    }

    public static String[] splitPathAndName(String path) {
        String[] result = new String[2];
        int nameSeparator = path.lastIndexOf('/');
        result[0] = path.substring(0, nameSeparator);
        result[1] = path.substring(nameSeparator + 1);
        return result;
    }

    public static boolean isFile(Resource resource) {
        return resource.isResourceType(ResourceUtil.TYPE_FILE) ||
                resource.isResourceType(ResourceUtil.TYPE_LINKED_FILE);
    }
}