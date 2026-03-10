package com.wearhouse.common.security.passport.gateway;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MutableHeaderHttpServletRequest extends HttpServletRequestWrapper {

    private final Map<String, List<String>> customHeaders = new HashMap<>();
    private final Set<String> removedHeaderNames = new LinkedHashSet<>();

    public MutableHeaderHttpServletRequest(HttpServletRequest request) {
        super(request);
    }

    public void putHeader(String name, String value) {
        customHeaders.put(name.toLowerCase(), new ArrayList<>(List.of(value)));
    }

    public void removeHeader(String name) {
        removedHeaderNames.add(name.toLowerCase());
    }

    @Override
    public String getHeader(String name) {
        String lower = name.toLowerCase();
        if (customHeaders.containsKey(lower)) {
            return customHeaders.get(lower).getFirst();
        }
        if (removedHeaderNames.contains(lower)) {
            return null;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        String lower = name.toLowerCase();
        if (customHeaders.containsKey(lower)) {
            return Collections.enumeration(customHeaders.get(lower));
        }
        if (removedHeaderNames.contains(lower)) {
            return Collections.emptyEnumeration();
        }
        return super.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<String> headerNames = super.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!removedHeaderNames.contains(name.toLowerCase())) {
                names.add(name);
            }
        }
        names.addAll(customHeaders.keySet());
        return Collections.enumeration(names);
    }
}
