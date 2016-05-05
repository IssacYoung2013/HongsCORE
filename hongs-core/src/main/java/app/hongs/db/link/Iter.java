package app.hongs.db.link;

import app.hongs.HongsError;
import app.hongs.HongsException;
import app.hongs.db.DB;
import app.hongs.util.Dict;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 遍历获取结果
 * @author Hong
 */
public class Iter implements Iterator<Map<String,Object>> {
    private final DB  db;
    private final Statement   ps;
    private final ResultSet   rs;
    private       Boolean     il = null;
    private ResultSetMetaData md = null;
    private Map<String,Class> td = null;

    public Iter(DB db, Statement ps, ResultSet rs) throws HongsException {
        this.db = db;
        this.ps = ps;
        this.rs = rs;
    }

    public DB getDB() {
        return db;
    }

    public Statement getStatement() {
        return ps;
    }

    public ResultSet getReusltSet() {
        return rs;
    }

    public ResultSetMetaData getMetaData() throws HongsException {
        if (md == null) {
            try {
                md = rs.getMetaData();
            } catch (SQLException ex) {
                throw new HongsException(0x10a0, ex);
            }
        }
        return md;
    }

    public Map<String,Class> getTypeDict() throws HongsException {
        if (td == null) {
            getMetaData();
            try {
                td = new LinkedHashMap();
                for (int i = 1, j = md.getColumnCount(); i <= j; i ++) {
                    td.put(md.getColumnLabel(i), Class.forName(md.getColumnClassName(i)));
                }
            } catch (  SQLException ex ) {
                throw new HongsException(0x10a2, ex);
            } catch (ClassNotFoundException ex) {
                throw new HongsException(0x10a2, ex);
            }
        }
        return td;
    }

    @Override
    public boolean hasNext() {
        // SQLite 等的 driver 并不支持 isLast
        // 故不得不在此靠执行 next 来获取状态
        if (il == null) {
            try {
                il = rs.next();
            } catch (SQLException ex) {
                this.over(   );
                throw new HongsError.Common(new HongsException(0x10a4, ex));
            }
        }
        return  il;
    }

    @Override
    public Map<String,Object> next( ) {
        // 判断是否到达末尾
        if (! hasNext()) {
            this.over();
            return null;
        }
        il  = null;

        // 获取当前字段类型
        try {
//              getMetaData();
            getTypeDict();
        } catch (HongsException ex ) {
            this.over();
            throw new HongsError.Common(ex);
        }

        // 获取行内每列数据
        try {
            int i = 0;
            Map<String,Object> row = new LinkedHashMap();
            if ( db.IN_OBJECT_MODE ) {
                for (Map.Entry<String,Class> et : td.entrySet()) {
                    //row.put(et.getKey(), rs.getObject(++ i, et.getState()));
                    Dict.put(row, rs.getObject(++ i, et.getValue()), (Object[])et.getKey().split("\\."));
                }
            } else {
                for (Map.Entry<String,Class> et : td.entrySet()) {
                    //row.put(et.getKey(), rs.getString(++ i /* No Type */ ));
                    Dict.put(row, rs.getString(++ i /* No Type */ ), (Object[])et.getKey().split("\\."));
                }
            }
            return  row;
        } catch (  SQLException ex ) {
            this.over();
            throw new HongsError.Common(new HongsException(0x10a6, ex));
        }
    }

    public void over( ) {
        if (td == null) {
            return;
        }
        try {
            db.closeResultSet( rs );
            db.closeStatement( ps );
        } catch (HongsException ex) {
            throw new HongsError.Common(ex);
        } finally {
            td = null;
            md = null;
        }
    }

}
