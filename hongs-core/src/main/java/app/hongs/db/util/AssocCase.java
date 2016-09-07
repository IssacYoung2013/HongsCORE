package app.hongs.db.util;

import app.hongs.Cnst;
import app.hongs.CoreLogger;
import app.hongs.HongsException;
import app.hongs.db.Model;
import app.hongs.db.Table;
import app.hongs.util.Dict;
import app.hongs.util.Synt;
import app.hongs.util.Tool;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 关联用例
 *
 * <p>此类可将外部请求数据转换为库表查询用例, 用法如下:</p>
 * <pre>
 *  FetchCase secondCase = new FetchCase(firstCase)
 *      .allow( table )
 *      .trans(request);
 *  allow(table) 可换成 allow(form), form 可使用 FormSet.getForm 来得到
 * </pre>
 *
 * @author Hongs
 */
public class AssocCase {

    /**
     * 可列举字段, 用于 FetchCase 的 Option
     */
    public  static final String  LISTABLE = "LISTABLE";
    /**
     * 可排序字段, 用于 FetchCase 的 Option, 未设置则取 LISTABLE
     */
    public  static final String  SORTABLE = "SORTABLE";
    /**
     * 可模糊搜索, 用于 FetchCase 的 Option
     */
    public  static final String  FINDABLE = "FINDABLE";
    /**
     * 可过滤字段, 用于 FetchCase 的 Option, 未设置则取 LISTABLE
     */
    public  static final String  FILTABLE = "FILTABLE";
    /**
     * 可存储字段, 用于 FetchCase 的 Option, 为设置则取 LISTABLE
     */
    public  static final String  SAVEABLE = "SAVEABLE";

    private static final Pattern anPt = Pattern.compile("^[\\w\\.]+(:|$)");
    private static final Pattern cnPt = Pattern.compile("^[\\w]$");

    private static final Set<String  /**/  > func = new HashSet( );
    private static final Map<String, String> rels = new HashMap( );
    static {
        func.add(Cnst.PN_KEY);
        func.add(Cnst.GN_KEY);
        func.add(Cnst.RN_KEY);
        func.add(Cnst.OB_KEY);
        func.add(Cnst.RB_KEY);
        func.add(Cnst.WD_KEY);
        func.add(Cnst.OR_KEY);
        func.add(Cnst.AR_KEY);
        rels.put(Cnst.EQ_REL, "=" );
        rels.put(Cnst.NE_REL, "!=");
        rels.put(Cnst.GT_REL, ">" );
        rels.put(Cnst.GE_REL, ">=");
        rels.put(Cnst.LT_REL, "<" );
        rels.put(Cnst.LE_REL, "<=");
        rels.put(Cnst.IN_REL, "IN");
        rels.put(Cnst.NI_REL, "NOT IN");
    }

    private final FetchCase  /**/  that;
    private final Map<String, Map> bufs;

    /**
     * 构造方法
     * @param caze 模板查询对象
     */
    public AssocCase(FetchCase caze) {
        if (caze == null) {
            throw new NullPointerException(AssocCase.class.getName()+": temp can not be null");
        }
        this.bufs = new HashMap();
        this.that = caze;
    }

    /**
     * 从库表设置许可字段
     * @param table
     * @return
     */
    public AssocCase allow(Table table) {
        Map af = allow(table, table, table.getAssocs(), null, null, new LinkedHashMap());

        bufs.put(LISTABLE, af);
        bufs.put(SORTABLE, af);
        bufs.put(FILTABLE, af);

        /**
         * 此处未将 FINDTABLE 设为当前所有字段
         * 模糊搜索比较特殊
         * 文本类型字段才行
         * 并不便于自动指派
         */

        return this;
    }

    /**
     * 从模型设置许可字段
     * @param model
     * @return
     */
    public AssocCase allow(Model model) {
        allow(model.table);

        if (model.listable != null) {
            allow(LISTABLE, model.listable);
        }
        if (model.sortable != null) {
            allow(SORTABLE, model.sortable);
        }
        if (model.findable != null) {
            allow(FINDABLE, model.findable);
        }
        if (model.filtable != null) {
            allow(FILTABLE, model.filtable);
        }

        return this;
    }

    /**
     * 自定义许可字段
     * @param an
     * @param fs
     * @return
     */
    public AssocCase allow(String an, String... fs) {
        if (fs.length == 1 && fs[0] == null) {
            that.delOption(an);
            bufs.remove(an);
            return this;
        }

        Map af = new LinkedHashMap();
        that.setOption(an, af);
        bufs.remove(an);

        Matcher m;
        String  k;
        for(String f : fs) {
            f = f.trim();
            if (f.isEmpty()) {
                continue;
            }

            m = anPt.matcher(f);
            if (m.matches()) {
                if (":".equals(m.group(1))) {
                    k = f.substring(0, m.end() -1);
                    f = f.substring(   m.end()   );
                } else {
                    k = f;
                }
                af.put( k, f);
            } else {
                af.put( f, f);
            }
        }

        return this;
    }

    /**
     * 从表单设置许可字段
     * 默认逗号分隔, 语句较复杂时, 可用分号分隔, 但不支持混用
     * 可用 table.getParams() 或 forms.get("@"), 后者可以调用 FormSet.getForm(String).get(String)
     * @param fc
     * @return
     */
    public AssocCase allow(Map fc) {
        String[] ks = new String[] {"listable", "sortable", "findable", "filtable"};
        for(String k : ks) {
            String s = fc.get(k).toString( ).trim( );
            if ("".equals(s) ) {
                continue ;
            }
            if (s.indexOf(';') != -1) {
                allow(k.toUpperCase(), s.split(";"));
            } else {
                allow(k.toUpperCase(), s.split(","));
            }
        }
        return this;
    }

    /**
     * 根据请求数据生成查询用例
     * 此处执行写当前 FetchCase
     * 故无法在设置好 allow 后反复 trans
     * @param rd
     * @return
     */
    public FetchCase trans(Map rd) {
        Map xd = new LinkedHashMap (rd);
        trans( that, xd );
        return that;
    }

    /**
     * 根据请求数据生成查询用例
     * 此处会克隆一份 FetchCase
     * 故可以在设置好 allow 后反复 tranc
     * @param rd
     * @return
     */
    public FetchCase tranc(Map rd) {
        FetchCase caze = that.clone(  );
        Map xd = new LinkedHashMap (rd);
        trans( caze, xd );
        return caze;
    }

    /**
     * 获取存储数据
     * 取出可列举字段对应的值
     * 以便用于创建和更新操作
     * @param rd 请求数据
     * @return
     */
    public Map<String, Object> saves(Map rd) {
        Map<String, String> af = allow( that, SAVEABLE );
        Map sd = new HashMap(  );

        for(Map.Entry<String, String> et : af.entrySet()) {
            String fn = et.getKey(  );
            String fc = et.getValue();
            Object fv = rd.get ( fn );
            if (fv != null) {
                sd.put( fc, fv );
            }
        }

        return  sd;
    }

    //** 内部工具方法 **/

    private void trans(FetchCase caze, Map rd) {
        if (rd == null || rd.isEmpty()) return;

        field(caze, Synt.asTerms(rd.remove(Cnst.RB_KEY)));
        order(caze, Synt.asTerms(rd.remove(Cnst.OB_KEY)));
        query(caze, Synt.asWords(rd.remove(Cnst.WD_KEY)));
        where(caze, rd);
    }

    private void field(FetchCase caze, Set<String> rb) {
        if (rb == null || rb.isEmpty()) return;

        Map<String, String> af = allow(caze, LISTABLE);
        Map<String, Set<String>> cf = new HashMap();
        Set<String> ic = new LinkedHashSet();
        Set<String> ec = new LinkedHashSet();
        Set<String> xc ;

        // 整理出层级结构, 方便处理通配符
        for(String  fn : af.keySet()) {
            String  k  ;
            int p = fn.lastIndexOf(".");
            if (p > -1) {
                k = fn.substring(0 , p)+".*";
            } else {
                k = "*";
            }

            Set<String> fs = cf.get ( k );
            if (fs == null  ) {
                fs  = new LinkedHashSet();
                cf.put(k, fs);
            }

            fs.add(fn);
        }

        for(String  fn : rb) {
            if (fn.startsWith("-") ) {
                fn = fn.substring(1);
                xc = ec;
            } else {
                xc = ic;
            }

            if (cf.containsKey(fn) ) {
                xc.addAll(cf.get(fn));
            } else
            if (af.containsKey(fn) ) {
                xc.add(fn);

                // 排除时, 先在包含中增加全部
                if (xc == ec) {
                    int p  = fn.lastIndexOf(".");
                    if (p != -1) {
                        fn = fn.substring(0 , p)+".*";
                    } else {
                        fn = "*";
                    }
                    ic.addAll(cf.get(fn));
                }
            }
        }

        // 默认没给就是全部
        if (ic.isEmpty() == true ) {
            ic.addAll(af.keySet());
        }

        // 取差集排排除字段
        if (ec.isEmpty() == false) {
            ic.removeAll(ec);
        }

        for(String  fn : ic) {
            String  fv = af.get(fn);
            caze.select(fv +" AS `"+ fn +"`");
        }
    }

    private void order(FetchCase caze, Set<String> ob) {
        if (ob == null || ob.isEmpty()) return;

        Map<String, String> af = allow(caze, SORTABLE);

        for(String fn : ob) {
            boolean desc = fn.startsWith("-");
            if (desc) {
                fn = fn.substring(1);
            }
            if (! af.containsKey(fn)) {
                continue;
            }
            fn = af.get (fn);
            if (desc && !fn.endsWith(" DESC")) {
                fn += " DESC";
            }
            caze.orderBy(fn);
        }
    }

    private void query(FetchCase caze, Set<String> wd) {
        if (wd == null || wd.isEmpty()) return;

        Map<String, String> af = allow(caze, FINDABLE);
        int  i = 0;
        int  l = wd.size( ) * af.size( );
        Object[]      ab = new Object[l];
        Set<String>   xd = new HashSet();
        StringBuilder sb = new StringBuilder();

        // 转义待查词, 避开通配符, 以防止歧义
        for(String  wb : wd) {
            xd.add("%" + Tool.escape( wb , "/%_[]" , "/") + "%" );
        }

        for(Map.Entry<String, String> et : af.entrySet()) {
            String fn = et.getValue();
            sb.append("(");
            for(String wb : xd) {
                ab[ i++ ] = wb;
                sb.append(fn).append( " LIKE ? ESCAPE '/' AND " );
            }
            sb.setLength(sb.length() - 5);
            sb.append(") OR " );
        }

        if (l > 0) {
            sb.setLength(sb.length() - 4);
            caze.filter ("(" + sb.toString() + ")" , ab );
        }
    }

    private void where(FetchCase caze, Map rd) {
        if (rd == null || rd.isEmpty()) return;

        Map<String, String> af = allow(caze, FILTABLE);

        for(Map.Entry<String, String> et : af.entrySet()) {
            String kn = et.getKey(  );
            String fn = et.getValue();
            Object fv = Dict.getParam(rd , kn);

            if (null == fv || "".equals( fv )) {
                continue; // 忽略空串, 但可以用 xxx.!eq= 查询空串
            }

            if (fv instanceof Map) {
                Map fm = new HashMap();
                fm.putAll(( Map ) fv );

                // 处理关系符号
                for(Map.Entry<String, String> el : rels.entrySet()) {
                    String rl = el.getKey(  );
                    Object rv = fm.remove(rl);
                    if (rv == null) continue ;
                    String rn = el.getValue();
                    if (Cnst.IN_REL.equals(rl)
                    ||  Cnst.NI_REL.equals(rl)) {
                        caze.filter(fn+" "+rn+" (?)", rv);
                    } else
                    if (!(rv instanceof Map)
                    &&  !(rv instanceof Set)
                    &&  !(rv instanceof Collection)) {
                        caze.filter(fn+" "+rn+ " ?" , rv);
                    } else {
                        CoreLogger.trace(AssocCase.class.getName()+": Can not set "+fn+" "+rn+" Collection");
                    }
                }

                // 清除功能参数
                for(String rl : func) {
                    fm.remove(rl);
                }
                    fm.remove("");

                // 如果还有剩余, 就当做 IN 来处理
                if (!fm.isEmpty()) {
                    fv = new LinkedHashSet( fm.values(  ) );
                    caze.filter(fn+" IN (?)", fv);
                }
            } else
            if (fv instanceof Set) {
                Set vs = (Set) fv;
                    vs.remove("");
                if(!vs.isEmpty( )) {
                    caze.filter(fn+" IN (?)", fv);
                }
            } else
            if (fv instanceof Collection) {
                Set vs = new LinkedHashSet((Collection) fv);
                    vs.remove("");
                if(!vs.isEmpty( )) {
                    caze.filter(fn+" IN (?)", fv);
                }
            } else {
                    caze.filter(fn+  " = ?" , fv);
            }
        }

        // 分组查询, 满足复杂的组合查询条件
        group(caze, Synt.declare(rd.get(Cnst.OR_KEY), Set.class), "OR" );
        group(caze, Synt.declare(rd.get(Cnst.AR_KEY), Set.class), "AND");
    }

    private void group(FetchCase caze, Set<Map> ar, String rn) {
        if (ar == null || ar.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        FetchCase   caxe = new FetchCase(    );

        for (Map rd : (Set<Map>) ar) {
            // 将查询用例里的条件清空
            // 然后将分组数据转为条件
            // 参数无需清空
            caxe.wheres.setLength(0);
            where(caxe, rd);

            if (caxe.wheres.length() > 0) {
                String wh = FetchCase.preWhere.matcher(caxe.wheres).replaceFirst("");
                sb.append('(').append(wh).append(')')
                  .append(' ').append(rn).append(' ');
            }
        }

        if (sb.length() > 0) {
            sb.setLength( sb.length()-rn.length()-2 );
            caze.wheres .append(   " AND "   )
                  .append('(').append(sb).append(')');
            caze.wparams.addAll(caxe.wparams );
        }
    }

    private Map allow(FetchCase caze, String on) {
        Map af  = bufs.get ( on );
        if (af != null ) {
            return af;
        }

        af = allowCheck(caze, on);
        bufs.put(on, af);
        return af;
    }

    private Map allowCheck(FetchCase caze, String on) {
        Map af = (Map) caze.getOption(on);

        // 相对列表字段增加减少
        if (af != null && ! LISTABLE.equals(on)) {
            Map xf = allowDiffs(af, caze);
            if (xf != null) {
                return xf ;
            }
        }

        if (af == null && ! LISTABLE.equals(on) && ! FINDABLE.equals(on)) {
            af = (Map) caze.getOption(LISTABLE);
        }
        if (af == null) {
            af =  new  HashMap( );
        }

        if (SAVEABLE.equals(on) ) {
            return allowSaves(af);
        } else {
            return allowTrans(af);
        }
    }

    private Map allowDiffs(Map af, FetchCase caze) {
        Map ic = new LinkedHashMap();
        Set ec = new HashSet();

        for(Object o : af.entrySet()) {
            Map.Entry e = (Map.Entry) o;
            String k = (String) e.getKey(  );
            String f = (String) e.getValue();
            if (k.startsWith("+")) {
                ic.put(k.substring(1), f);
            } else
            if (k.startsWith("-")) {
                ec.add(k.substring(1));
            }
        }

        if (ic.isEmpty() && ec.isEmpty()) {
            return null;
        }

        Map xf = new LinkedHashMap(allow(caze, LISTABLE));

        for(Object o : xf.entrySet()) {
            Map.Entry e = (Map.Entry) o;
            String k = (String) e.getKey(  );
            String f = (String) e.getValue();
            xf.put(k, f);
        }
        for(Object k : ec) {
            xf.remove(k);
        }

        return xf;
    }

    private Map allowTrans(Map af) {
        Map al = new LinkedHashMap();

        for(Object ot : af.entrySet()) {
            Map.Entry et = (Map.Entry) ot;
            String k = (String) et.getKey(  );
            String f = (String) et.getValue();
            if (cnPt.matcher(f).matches()) {
                f = ".`"+ f +"`";
            }
                al.put(k, f);
        }

        return  al;
    }

    private Map allowSaves(Map af) {
        Map al = new LinkedHashMap();

        for(Object ot : af.entrySet()) {
            Map.Entry et = (Map.Entry) ot;
            String k = (String) et.getKey(  );
            String f = (String) et.getValue();
            if (cnPt.matcher(f).matches()) {
                al.put(k, f);
            }
        }

        return  al;
    }

    private Map allow(Table table, Table assoc, Map ac, String tn, String qn, Map al) {
        String  ax , tx  ;

        /**
         * 三个变量: 层级名(qn), 名前缀(ax), 表前缀(tx)
         * 第一层时, 无需加名前缀, 无关联表前缀也不用加
         * 第二层时, 需将表名作为名前缀, 下级需带层级名
         */
        if (null ==  qn  ) {
            qn = "";
            ax = "";
            tx = ".";
        } else
        if ("".equals(qn)) {
            qn = tn;
            ax = qn + ".";
            tx = "`"+ tn +"`.";
        } else {
            qn = qn + "."+ tn ;
            ax = qn + ".";
            tx = "`"+ tn +"`.";
        }

        try {
            Map fs = assoc.getFields( );
            for(Object n : fs.keySet()) {
                String k = (String) n;
                String f = (String) n;
                k = ax +/**/ k /**/;
                f = tx +"`"+ f +"`";
                al.put(k , f);
            }
        }
        catch (HongsException ex) {
            CoreLogger.error( ex);
        }

        if (ac != null && !ac.isEmpty()) {
            Iterator it = ac.entrySet().iterator(  );
            while (it.hasNext()) {
                Map.Entry et = (Map.Entry) it.next();
                Map       tc = (Map) et.getValue(  );
                String jn = (String) tc.get ("join");

                // 不是 JOIN 的不理会
                if (! "INNER".equals(jn)
                &&  !  "LEFT".equals(jn)
                &&  ! "RIGHT".equals(jn)
                &&  !  "FULL".equals(jn)) {
                    continue;
                }

                try {
                    ac = (Map) tc.get("assocs");
                    tn = (String) et.getKey(  );
                    assoc = table.getAssocInst(tn);
                    allow(table, assoc, ac, tn,qn, al);
                }
                catch (HongsException ex) {
                    CoreLogger.error( ex);
                }
            }
        }

        return  al;
    }

}
