package app.hongs.db.util;

import app.hongs.Core;
import app.hongs.HongsException;
import app.hongs.db.Table;
import app.hongs.util.Dict;
import app.hongs.util.Synt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 关联工具
 * 
 * <h3>异常代码:</h3>
 * <pre>
 * 区间: 0x10c0~0x10cf
 * 0x10c2 无法识别的关联方式(LINK)
 * 0x10c4 无法识别的关联类型(JOIN)
 * 0x10c6 找不到指定的关联表(LINK)
 * 0x10c8 找不到指定的关联表(JOIN)
 * 0x10ca 关联数据类型必须为Map
 * 0x10cc 关联数据类型必须为Map或List
 * </pre>
 *
 * @author Hongs
 */
public class FetchMost {

  /**
   * 关联查询
   * @param table  主表
   * @param caze   查询体
   * @param assocs 关联配置
   * @return 结果列表
   * @throws HongsException
   */
  public static List fetchMore
    (Table table, FetchCase caze, Map assocs)
  throws HongsException {
    if (assocs == null) assocs = new HashMap();

    List<Map> lnks = new ArrayList(  );
    fetchMore(table, caze, assocs, lnks, null);

    // Added in 2015/12/08
    // 利用匿名的关联配置
    // 来作为表的默认查询
    Map assoc  = (Map) assocs.get("@");
    if (assoc != null) {
        buildCase( caze, assoc /***/ );
    }

    // Fixed in 2015/10/22
    // 当关联表也要查全部
    // 会导致不查主表字段
    // 故只要未指定主表字段即取全部
    if (!caze.hasSelect()) {
        caze.select(".*");
    }

    List rows = table.db.fetchMore(caze);
    fetchMore(table, caze,  rows , lnks);

    return rows;
  }

  private static void fetchMore
    (Table table, FetchCase caze, Map assocs, List lnks2, String pn)
  throws HongsException {
    Set tns = (Set)caze.getOption("ASSOCS");
    Set tps = (Set)caze.getOption("ASSOC_TYPES");
    Set jns = (Set)caze.getOption("ASSOC_JOINS");
    Set jis = new HashSet(Arrays.asList("MERGE", ""));
    String tn = caze.name;
    if (tn == null || tn.length() == 0)
           tn = caze.tableName;

    for(Map.Entry et : (Set<Map.Entry>)assocs.entrySet()) {
        Map assoc = (Map) et.getValue();
        String tp = (String)assoc.get("type");
        String jn = (String)assoc.get("join");
        String an = (String)assoc.get("name");
        String rn = (String)assoc.get("tableName");
        if (rn == null || rn.length() == 0) rn = an ;
        if (rn == null || rn.length() == 0) continue;

        // 检查是否许可关联
        if (tns != null && !tns.contains(rn) && !tns.contains(an)) {
            continue;
        }
        if (tps != null && !tps.contains(tp)) {
            continue;
        }
        if (jn  != null && !jis.contains(jn)) {
        if (jns != null && !jns.contains(jn)) {
            continue;
        }}  else {
            // 非JOIN表先放到一边
            assoc.put("assocName", tn);
            lnks2.add( assoc );
            continue;
        }

        Map  assocs2 = (Map)assoc.get("assocs");
        String fk = (String)assoc.get("foreignKey");
        String pk = (String)assoc.get("primaryKey");
        Table table2 = table.db.getTable(rn);
        FetchCase caze2 =  caze.gotJoin (an).from(table2.tableName);

        // 建立关联关系
        if ("BLS_TO".equals(tp)) {
            // 上级外键连接下级主键
            if (pk == null) pk = table2.primaryKey;
            if (tn == null || tn.length() == 0) {
                fk =         ":`"+fk+"`";
            } else {
                fk = "`"+tn+"`.`"+fk+"`";
            }   pk = "`"+an+"`.`"+pk+"`";
        } else
        if ("HAS_ONE".equals(tp)) {
            // 上级主键连接下级外键
            if (pk == null) pk = table .primaryKey;
            if (tn == null || tn.length() == 0) {
                pk =         ":`"+pk+"`";
            } else {
                pk = "`"+tn+"`.`"+pk+"`";
            }   fk = "`"+an+"`.`"+fk+"`";
        } else
        if ("HAS_MANY".equals(tp) || "HAS_MORE".equals(tp)) {
            throw new HongsException(0x10c2,  "Unsupported assoc type '"+tp+"'");
        }
        else {
            throw new HongsException(0x10c2, "Unrecognized assoc type '"+tp+"'");
        }
        caze2.on( pk +"="+ fk );

        // 转化关联类型
        byte ji;
        if ( "LEFT".equals(jn)) {
            ji = FetchCase.LEFT;
        } else
        if ("RIGHT".equals(jn)) {
            ji = FetchCase.RIGHT;
        } else
        if ( "FULL".equals(jn)) {
            ji = FetchCase.FULL;
        } else
        if ("INNER".equals(jn)) {
            ji = FetchCase.INNER;
        } else
        if ("CROSS".equals(jn)) {
            throw new HongsException(0x10c4,  "Unsupported assoc join '"+jn+"'");
        }
        else {
            throw new HongsException(0x10c4, "Unrecognized assoc join '"+jn+"'");
        }
        caze2.by( ji );

        // 添加关联层级
        String pu = an;
        if (null != pn) {
               pu = pn +"."+ pu;
        }
        caze2.in( pu );

        buildCase(caze2, assoc);

        if ( assocs2 !=  null ) {
            fetchMore(table2, caze2, assocs2, lnks2, pu);
        }

        // Fixed in 2015/10/22
        // 因 JOIN 无法用 * 查询(可能导致重名), 故需要追加全部字段
        if ( ! caze.hasSelect( ) && ! caze2.hasSelect( ) ) {
            Set<String> cols = table2.getFields().keySet();
            for(String  col  : cols) {
                caze2.select(".`"+ col +"` AS `"+an+"."+ col +"`");
            }
        }
    }
  }

  private static void fetchMore
    (Table table, FetchCase caze, List rows2, List lnks2)
  throws HongsException {
    Set tns = (Set)caze.getOption("ASSOCS");
    Set tps = (Set)caze.getOption("ASSOC_TYPES");
    FetchMore join = new FetchMore( rows2 );

    while (!lnks2.isEmpty()) {
        List lnkz2 = new ArrayList(  );
    for(Map assoc : (List<Map>) lnks2) {
        String tp = (String)assoc.get("type");
        String jn = (String)assoc.get("join");
        String an = (String)assoc.get("name");
        String rn = (String)assoc.get("tableName");
        String tn = (String)assoc.get("assocName");
        if (rn == null || rn.length() == 0) rn = an ;
        if (rn == null || rn.length() == 0) continue;

        // 检查是否许可关联
        if (tns != null && !tns.contains(rn) && !tns.contains(an)) {
            continue;
        }
        if (tps != null && !tps.contains(tp)) {
            continue;
        }

        Map  assocs2 = (Map)assoc.get("assocs");
        String fk = (String)assoc.get("foreignKey");
        String pk = (String)assoc.get("primaryKey");
        Table table2 = table.db.getTable(rn);
        FetchCase caze2 =  caze.gotJoin (an).from(table2.tableName);

        // 准备关联关系
        if ("BLS_TO".equals(tp)) {
            // 上级外键连接下级主键, 交换主外键
            String xk = fk; fk = pk; pk = xk;
            if (fk == null) fk = table2.primaryKey;
            caze2.setOption("ASSOC_MULTI" , false);
        } else
        if ("HAS_ONE".equals(tp)) {
            // 上级主键连接下级外键
            if (pk == null) pk = table .primaryKey;
            caze2.setOption("ASSOC_MULTI" , false);
        } else
        if ("HAS_MANY".equals(tp)) {
            // 上级主键连接下级外键
            if (pk == null) pk = table .primaryKey;
            caze2.setOption("ASSOC_MULTI" , true );
        } else
        if ("HAS_MORE".equals(tp)) {
            // 上级主键连接下级外键
            if (pk == null) pk = table .primaryKey;
            caze2.setOption("ASSOC_MULTI" , true );
            // 将下层数据合并到本层
            if (assocs2 != null) {
                for(Map ass  : ( Collection<Map> ) assocs2.values()) {
                    if (ass.containsKey ( "join" ) != true) {
                        ass.put( "join", "MERGE" );
                    }
                }
            }
        }
        else {
            throw new HongsException(0x10c2, "Unrecognized assoc type '"+tp+"'");
        }
        if (tn != null && tn.length() != 0 && !tn.equals(caze.name)) {
            pk = tn +"."+ pk;
        }

        // 合并关联模式
        caze2.setOption (  "ASSOC_MERGE"  ,  "MERGE".equals(  jn  ));

        buildCase(caze2, assoc);

        if ( assocs2 !=  null ) {
            fetchMore(table2, caze2, assocs2, lnkz2, null);
        }

        join.join (table2, caze2, fk.startsWith(":") ? fk.substring(1) : fk, pk);
    }
        lnks2 = lnkz2;
    }
  }

  private static void buildCase(FetchCase caze, Map assoc) {
    String sql;
    sql = (String) assoc.get("select");
    if (sql != null) {
        caze.setSelect (sql); // rb 参数将无效
    }
    sql = (String) assoc.get("orderBy");
    if (sql != null) {
        caze.setOrderBy(sql); // ob 参数将无效
    }
    sql = (String) assoc.get("filter" );
    if (sql != null && sql.length() != 0) {
        caze.where(sql); // 此处为附加条件, 外部条件仍有效
    }
    sql = (String) assoc.get("groupBy");
    if (sql != null) {
        caze.setGroupBy(sql);
    }
    sql = (String) assoc.get("having" );
    if (sql != null && sql.length() != 0) {
        caze.having(sql);
    }
    sql = (String) assoc.get( "limit" );
    if (sql != null && sql.length() != 0) {
        caze.limit (Synt.asserts(sql, 1));
    }
  }

    /**
     * 关联更新
     *
     * @param table  主表
     * @param rows   要插入的数据
     * @param keys   判断改变的键
     * @param where  更新/删除范围
     * @param params where 的参数
     * @throws HongsException
     */
    public static void updateMore(
        Table           table,
        List<Map>       rows,
        String[ ]       keys,
        String          where,
        Object...       params
    )   throws HongsException
    {
        List<Object> params1 = Arrays.asList(params);
        List<Object> params2;
        Object[]     params3;

        StringBuilder where2 = new StringBuilder(where);
        String        where3;
        for (String k : keys)
        {
            where2.append(" AND `").append(k).append("` = ?");
        }
        where3 = where2.toString( );

        List ids = new ArrayList( );

        // 状态键值, 2015/12/15
        String rstat = table.getField( "state" );
        String vstat = table.getState("default");

        for (Map row : rows)
        {
            // 默认状态, 2015/12/15
            if (rstat != null && vstat != null && !row.containsKey(rstat)) {
                row.put( rstat , vstat );
            }

            params2 = new ArrayList(params1);
            for (String k : keys )
            {
                params2.add(row.get(k) );
            }
            params3 = params2.toArray( );

            String sql = "SELECT `" + table.primaryKey + "` FROM `" + table.tableName + "` WHERE " + where2;
            Map<String, Object> one = table.db.fetchOne( sql , params3 );
            if (!one.isEmpty())
            {
                // 有则更新
                if (!row.containsKey(table.primaryKey) || "".equals(row.get(table.primaryKey)))
                {
                    row.put(table.primaryKey, one.get(table.primaryKey));
                }
                table.update(row, where3, params3);
            }
            else
            {
                // 没则插入
                if (!row.containsKey(table.primaryKey) || "".equals(row.get(table.primaryKey)))
                {
                    row.put(table.primaryKey, Core.getUniqueId(/*SID*/));
                }
                table.insert(row);
            }

            ids.add(row.get(table.primaryKey));
        }

        // 删除多余
        where2  = new StringBuilder(where);
        where2.append(" AND `").append(table.primaryKey).append("` NOT IN (?)");
        params2 = new ArrayList( params1 ); params2.add( ids );
        table .delete(  where2.toString( ), params2.toArray());
    }

  /**
   * 关联插入
   *
   * 关联配置中有指定 unique 键的话, 会调用 updateMore 进行更新
   *
   * @param table  主表
   * @param assocs 关联配置
   * @param values 要插入的数据
   * @throws app.hongs.HongsException
   */
  public static void insertMore(Table table, Map assocs, Map values)
    throws HongsException
  {
    if ( assocs == null || assocs.isEmpty( ) ) return;

    String id = (String) values.get(table.primaryKey);

    Iterator it = assocs.entrySet().iterator();
    while (it.hasNext())
    {
      Map.Entry entry = (Map.Entry) it.next( );
      Map config = (Map)entry.getValue();

      String type = (String)config.get("type");
      String name = (String)config.get("name");
      String tableName  = (String)config.get("tableName" );
      String foreignKey = (String)config.get("foreignKey");

      if (!values.containsKey(name))
      {
        continue;
      }
      if (!type.equals("HAS_ONE") && !type.equals("HAS_MANY") && !type.equals("HAS_MORE"))
      {
        continue;
      }
      if (tableName == null || tableName.length() == 0)
      {
          tableName =  name;
      }

      Table tb = table.db.getTable(tableName);

      // 都整理成List方便处理
      Object subValues  = values.get(name);
      List   subValues2 = new ArrayList( );
      if ("HAS_ONE".equals(type))
      {
        if (subValues instanceof Map)
        {
          subValues2.add( subValues );
        }
        else
        {
          throw new HongsException(0x10ca,
          "Sub data type for table '"+tb.name+"' must be Map");
        }
      }
      else
      {
        if (subValues instanceof Map)
        {
          subValues2.addAll(((Map)subValues).values());
        }
        else
        if (subValues instanceof Collection)
        {
          subValues2.addAll(( Collection ) subValues );
        }
        else
        {
          throw new HongsException(0x10cc,
          "Sub data type for table '"+tb.name+"' must be Map or List");
        }
      }

      /**
       * Add by Hongs on 2016/4/13
       * 当存在 filter 条件时, 可以解析 filter 条件里的值作为要插入的限制值
       */
      String wh = (String) config.get("filter");
      if (wh != null && wh.length() != 0)
      {
        Pattern pat = Pattern.compile("(?:`(.*?)`|([\\w]+))\\s*=\\s(?:'(.*?)'|([\\d\\.]+))");
        Matcher mat = pat.matcher(wh);
        Map     map = new HashMap();
        while ( mat.find()) {
            String n = mat.group(1);
            if (null == n ) {
                   n = mat.group(2);
            }
            String v = mat.group(3);
            if (null == v ) {
                   v = mat.group(4);
            }
            map.put( n, v );
        }
        // 填充约束
        Iterator it2 = subValues2.iterator();
        while  ( it2.hasNext() )
        {
          Map subValues3 = (Map) it2.next( );
          subValues3.putAll(map);
        }
        // 附加条件
        wh = "`"+foreignKey+"`=? AND " + wh ;
      }
      else
      {
        wh = "`"+foreignKey+"`=?";
      }

      /**
       * Add by Hongs on 2016/4/13
       * 当存在 convey 字段时, 可以根据 convey 从上层提取需要同步传递的数据
       */
      String cs = (String) config.get("convey");
      if (cs != null && cs.length() != 0)
      {
        String[] cz = cs.split("\\s*,\\s*" );

        // 提取数据
        Map cd = new HashMap();
        for(String cn  :  cz )
        {
            cd.put(cn  ,  values.get( cn ) );
        }

        // 传递数据
        Iterator it2 = subValues2.iterator();
        while  ( it2.hasNext() )
        {
          Map subValues3 = (Map) it2.next( );
          subValues3.putAll (cd);
        }
      }

      /**
       * Add by Hongs on 2013/6/6
       * 有时候子数据会被其他数据引用, 如果更新子数据, 子数据的ID就会改变;
       * 通常这种情况存在以下规则: 如果某些字段值没发生改变则不要重新插入;
       * 所以当有指定 unique 时, 可使用 updateMore 方法更新数据, 其原理为:
       * 找出没改变的数据并更新, 然后插入新增数据, 最后删除更新和新增之外的数据.
       */
      String ks = (String) config.get("unique");
      if (ks == null || ks.length() == 0)
      {
          // 2016/4/15, 也可以读取表自身的唯一键
          ks = Dict.getValue(tb.getAssocs(), "", "@", "unique");
      }
      if (ks != null && ks.length() != 0)
      {
        String[] kz = ks.split("\\s*,\\s*" );

        // 填充外键
        Iterator it2 = subValues2.iterator();
        while  ( it2.hasNext() )
        {
          Map subValues3 = (Map) it2.next( );
          subValues3.put ( foreignKey , id );
        }

        updateMore(tb, subValues2, kz, wh, id);
      }
      else
      {
        // 先删除旧数据
        tb.delete("`"+foreignKey+"`=?" , id);

        // 再插入新数据
        Iterator it2 = subValues2.iterator();
        while  ( it2.hasNext() )
        {
          Map subValues3 = (Map) it2.next( );
          subValues3.put ( foreignKey , id );

          // 如果存在主键而没给定主键值,则帮其添加上唯一ID
          if (tb.primaryKey != null
          &&  tb.primaryKey.length() != 0
          &&  ! subValues3.containsKey( tb.primaryKey /**/ ))
          {
            subValues3.put(tb.primaryKey, Core.getUniqueId());
          }

          tb.insert(subValues3);
        }
      }
    }
  }

  /**
   * 关联删除
   *
   * @param table  主表
   * @param assocs 关联配置
   * @param ids    要删除的外键
   * @throws app.hongs.HongsException
   */
  public static void deleteMore(Table table, Map assocs, Object... ids)
    throws HongsException
  {
    if ( assocs == null || assocs.isEmpty( ) ) return;

    Iterator it = assocs.entrySet().iterator();
    while (it.hasNext())
    {
      Map.Entry entry = (Map.Entry) it.next( );
      Map config = (Map)entry.getValue();

      String type = (String)config.get("type");
      String name = (String)config.get("name");
      String tableName  = (String)config.get("tableName" );
      String foreignKey = (String)config.get("foreignKey");

      if (!type.equals("HAS_ONE") && !type.equals("HAS_MANY") && !type.equals("HAS_MORE"))
      {
          continue;
      }
      if (tableName == null || tableName.length() == 0)
      {
          tableName  = name;
      }

      // 获取下级的下级的ID
      Table tbl = table.db.getTable(tableName);
      List  idx = null;
      if (tbl.primaryKey != null
      &&  tbl.primaryKey.length() != 0)
      {
        List<Map> lst  =  tbl.fetchMore
        (
          new FetchCase()
            .select ("`"+ tbl.primaryKey +"`")
            .where  ("`"+foreignKey+"`=?",ids)
        );
        idx = new ArrayList();
        for ( Map row : lst )
        {
          idx.add(row.get(tbl.primaryKey));
        }
      }

      // 下级伪删除同样有效
      tbl.delete("`"+foreignKey+"`=?",ids);

      // 递归删除下级的下级
      if (idx != null && ! idx.isEmpty() )
      {
        deleteMore(tbl , tbl.getAssocs() , idx.toArray());
      }
    }
  }

}
