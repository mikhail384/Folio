package ru.folio.notes;

/** Explicit server allowlist; automatic learning requires an updated backend. */
final class AgentEndpoints {
    static final String SITE="https://109.66.236.217:17443";
    static final String LEGACY_SITE="https://folio-canvas-link.mikhailllm.chatgpt.site";
    static final String PREVIOUS_VPS="https://31-77-128-190.cdn-one.org:17443";
    static final String TEST_VPS=SITE;
    private AgentEndpoints(){}
    static boolean allowed(String url){return SITE.equals(url)||LEGACY_SITE.equals(url)||PREVIOUS_VPS.equals(url);}
    static boolean automatic(String url){return SITE.equals(url)||LEGACY_SITE.equals(url);}
}
