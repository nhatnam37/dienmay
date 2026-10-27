package com.dienmay.common.pagination;

import java.util.Locale;

public final class SearchTerms {
    private SearchTerms(){}
    /** Các query phải khai báo ESCAPE '!'; không escape trên request rồi escape lại tại repository. */
    public static String contains(String value,boolean lowercase){
        if(value==null||value.isBlank())return null;
        String term=value.strip();if(lowercase)term=term.toLowerCase(Locale.ROOT);
        return "%"+escape(term)+"%";
    }
    public static String escape(String value){return value.replace("!","!!").replace("%","!%").replace("_","!_");}
}
