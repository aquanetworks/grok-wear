package si.zya.grokwear;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Indent-based YAML subset used by Clash Meta subscriptions (maps, lists, scalars, comments).
 * Anchors, tags and merge keys are rejected rather than half-parsed.
 */
final class ClashYaml {
    static Object load(String text)throws Exception{
        if(text==null)throw new GrokCore.Failure("Empty Clash YAML.",true);
        String[] lines=text.replace("\r\n","\n").replace('\r','\n').split("\n",-1);
        Parser p=new Parser(lines);Object value=p.parse(-1);
        if(p.i<lines.length&&!p.restBlank())throw new GrokCore.Failure("Clash YAML has leftover text.",true);
        return value==null?new LinkedHashMap<String,Object>():value;
    }
    @SuppressWarnings("unchecked")
    static List<Map<String,Object>> proxies(String text)throws Exception{
        Object root=load(text);
        if(root instanceof List)return maps((List<?>)root);
        if(!(root instanceof Map))throw new GrokCore.Failure("Clash YAML is not a mapping.",true);
        Object proxies=((Map<String,Object>)root).get("proxies");
        if(!(proxies instanceof List))throw new GrokCore.Failure("Clash YAML has no proxies list.",true);
        return maps((List<?>)proxies);
    }
    static String dump(Object value){StringBuilder out=new StringBuilder();dump(out,value,0,false);return out.toString();}
    private static List<Map<String,Object>> maps(List<?> raw)throws Exception{
        ArrayList<Map<String,Object>> out=new ArrayList<>();
        for(Object item:raw){
            if(!(item instanceof Map))throw new GrokCore.Failure("Clash proxy entry is not a mapping.",true);
            LinkedHashMap<String,Object> copy=new LinkedHashMap<>();
            for(Map.Entry<?,?> e:((Map<?,?>)item).entrySet())copy.put(String.valueOf(e.getKey()),e.getValue());
            out.add(copy);
        }
        if(out.isEmpty())throw new GrokCore.Failure("Clash YAML has no proxies.",true);
        return out;
    }
    private static void dump(StringBuilder out,Object value,int indent,boolean listItem){
        if(value instanceof Map){
            Map<?,?> map=(Map<?,?>)value;
            if(map.isEmpty()){pad(out,listItem?0:indent);out.append("{}\n");return;}
            boolean first=true;
            for(Map.Entry<?,?> e:map.entrySet()){
                if(!first||!listItem)pad(out,indent);first=false;
                out.append(key(String.valueOf(e.getKey()))).append(':');
                Object child=e.getValue();
                if(child instanceof Map || child instanceof List){out.append('\n');dump(out,child,indent+2,false);}
                else{out.append(' ').append(scalar(child)).append('\n');}
            }
            return;
        }
        if(value instanceof List){
            List<?> list=(List<?>)value;
            if(list.isEmpty()){pad(out,listItem?0:indent);out.append("[]\n");return;}
            for(Object item:list){
                pad(out,indent);out.append("- ");
                if(item instanceof Map || item instanceof List){
                    if(item instanceof Map)dump(out,item,indent+2,true);
                    else{out.append('\n');dump(out,item,indent+2,false);}
                }else out.append(scalar(item)).append('\n');
            }
            return;
        }
        pad(out,listItem?0:indent);out.append(scalar(value)).append('\n');
    }
    private static void pad(StringBuilder out,int n){for(int i=0;i<n;i++)out.append(' ');}
    private static String key(String raw){return needsQuote(raw)?quote(raw):raw;}
    private static String scalar(Object value){
        if(value==null)return "null";
        if(value instanceof Boolean)return ((Boolean)value)?"true":"false";
        if(value instanceof Number)return value.toString();
        String text=String.valueOf(value);return needsQuote(text)?quote(text):text;
    }
    private static boolean needsQuote(String text){
        if(text.isEmpty())return true;
        char c=text.charAt(0);
        if(c=='&'||c=='*'||c=='!'||c=='%'||c=='@'||c=='`'||c=='{'||c=='['||c==','||c=='#'||c=='|'||c=='>'||c=='\''||c=='"'||c==' ')return true;
        if(text.startsWith("- ")||text.contains(": ")||text.contains("\n")||text.contains("#"))return true;
        String lower=text.toLowerCase(Locale.ROOT);
        return lower.equals("true")||lower.equals("false")||lower.equals("null")||lower.equals("yes")||lower.equals("no")||looksNumber(text);
    }
    private static boolean looksNumber(String text){
        try{Long.parseLong(text);return true;}catch(NumberFormatException ignored){}
        try{Double.parseDouble(text);return true;}catch(NumberFormatException ignored){return false;}
    }
    private static String quote(String text){
        StringBuilder out=new StringBuilder(text.length()+2).append('"');
        for(int i=0;i<text.length();i++){
            char c=text.charAt(i);
            if(c=='\\'||c=='"')out.append('\\');
            if(c=='\n'){out.append("\\n");continue;}
            if(c=='\r'){out.append("\\r");continue;}
            out.append(c);
        }
        return out.append('"').toString();
    }

    private static final class Parser {
        final String[] lines;int i;
        Parser(String[] lines){this.lines=lines;}
        boolean restBlank(){
            for(int n=i;n<lines.length;n++)if(!strip(lines[n]).isEmpty())return false;
            return true;
        }
        Object parse(int parent)throws Exception{
            skip();if(i>=lines.length)return null;
            int indent=indent(lines[i]);if(parent>=0&&indent<=parent)return null;
            String trimmed=strip(lines[i]);
            if(trimmed.startsWith("- ")||trimmed.equals("-"))return parseList(parent);
            return parseMap(parent);
        }
        Object parseMap(int parent)throws Exception{
            LinkedHashMap<String,Object> map=new LinkedHashMap<>();
            while(i<lines.length){
                skip();if(i>=lines.length)break;
                int indent=indent(lines[i]);if(indent<=parent)break;
                String trimmed=strip(lines[i]);
                if(trimmed.startsWith("- ")||trimmed.equals("-"))break;
                int colon=splitKey(trimmed);if(colon<0)throw new GrokCore.Failure("Clash YAML is missing a key.",true);
                String key=unquote(trimmed.substring(0,colon).trim());
                String rest=trimmed.substring(colon+1).trim();
                i++;
                if(rest.isEmpty()){
                    skip();
                    if(i>=lines.length||indent(lines[i])<=indent)map.put(key,new LinkedHashMap<String,Object>());
                    else map.put(key,parse(indent));
                }else map.put(key,scalar(rest));
            }
            return map;
        }
        Object parseList(int parent)throws Exception{
            ArrayList<Object> list=new ArrayList<>();
            while(i<lines.length){
                skip();if(i>=lines.length)break;
                int indent=indent(lines[i]);if(indent<=parent)break;
                String trimmed=strip(lines[i]);
                if(!(trimmed.startsWith("- ")||trimmed.equals("-")))break;
                String rest=trimmed.equals("-")?"":trimmed.substring(2).trim();
                i++;
                if(rest.isEmpty())list.add(parse(indent));
                else if(splitKey(rest)>=0){
                    // Inline map on the list dash: `- name: foo`
                    LinkedHashMap<String,Object> item=new LinkedHashMap<>();
                    int colon=splitKey(rest);
                    String key=unquote(rest.substring(0,colon).trim());
                    String value=rest.substring(colon+1).trim();
                    if(value.isEmpty())item.put(key,parse(indent));
                    else item.put(key,scalar(value));
                    Object more=parseMap(indent);
                    if(more instanceof Map)item.putAll((Map<String,Object>)more);
                    list.add(item);
                }else list.add(scalar(rest));
            }
            return list;
        }
        void skip()throws Exception{
            while(i<lines.length){
                String t=strip(lines[i]);
                if(t.isEmpty()||t.startsWith("#")){i++;continue;}
                if(t.equals("---")||t.equals("...")){i++;continue;}
                if(t.startsWith("%")||t.startsWith("&")||t.startsWith("*")||t.startsWith("<<:"))
                    throw new GrokCore.Failure("Clash YAML anchors are not supported.",true);
                break;
            }
        }
        static int indent(String line){
            int n=0;while(n<line.length()&&line.charAt(n)==' ')n++;return n;
        }
        static String strip(String line){
            String t=line.replace("\t","  ");
            int hash=unquotedHash(t);if(hash>=0)t=t.substring(0,hash);
            return t.trim();
        }
        static int unquotedHash(String line){
            boolean q=false;char quote=0;
            for(int i=0;i<line.length();i++){
                char c=line.charAt(i);
                if(q){if(c==quote&&(i==0||line.charAt(i-1)!='\\'))q=false;continue;}
                if(c=='\''||c=='"'){q=true;quote=c;continue;}
                if(c=='#'&&(i==0||line.charAt(i-1)==' '))return i;
            }
            return -1;
        }
        static int splitKey(String trimmed){
            boolean q=false;char quote=0;
            for(int i=0;i<trimmed.length();i++){
                char c=trimmed.charAt(i);
                if(q){if(c==quote&&(i==0||trimmed.charAt(i-1)!='\\'))q=false;continue;}
                if(c=='\''||c=='"'){q=true;quote=c;continue;}
                if(c==':'&&(i+1==trimmed.length()||trimmed.charAt(i+1)==' '))return i;
            }
            return -1;
        }
        static Object scalar(String raw)throws Exception{
            if(raw.equals("~")||raw.equalsIgnoreCase("null"))return null;
            if(raw.equalsIgnoreCase("true")||raw.equalsIgnoreCase("yes"))return Boolean.TRUE;
            if(raw.equalsIgnoreCase("false")||raw.equalsIgnoreCase("no"))return Boolean.FALSE;
            if((raw.startsWith("\"")&&raw.endsWith("\""))||(raw.startsWith("'")&&raw.endsWith("'")))return unquote(raw);
            try{if(raw.matches("-?\\d+"))return Long.parseLong(raw);}catch(NumberFormatException ignored){}
            try{if(raw.matches("-?\\d+\\.\\d+"))return Double.parseDouble(raw);}catch(NumberFormatException ignored){}
            return unquote(raw);
        }
        static String unquote(String raw)throws Exception{
            if(raw.length()>=2&&((raw.startsWith("\"")&&raw.endsWith("\""))||(raw.startsWith("'")&&raw.endsWith("'")))){
                String inner=raw.substring(1,raw.length()-1);
                if(raw.charAt(0)=='"')inner=inner.replace("\\n","\n").replace("\\r","\r").replace("\\\"","\"").replace("\\\\","\\");
                return inner;
            }
            return raw;
        }
    }
}
