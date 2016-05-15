package app.hongs.dh.lucene;

import app.hongs.Cnst;
import app.hongs.Core;
import app.hongs.CoreConfig;
import app.hongs.CoreLocale;
import app.hongs.CoreLogger;
import app.hongs.HongsError;
import app.hongs.HongsException;
import app.hongs.HongsUnchecked;
import app.hongs.action.FormSet;
import app.hongs.dh.IEntity;
import app.hongs.dh.ITrnsct;
import app.hongs.dh.ModelView;
import app.hongs.util.Data;
import app.hongs.util.Dict;
import app.hongs.util.Synt;
import app.hongs.util.Tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.LongField;
import org.apache.lucene.document.FloatField;
import org.apache.lucene.document.DoubleField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.NumericRangeQuery;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortedNumericSortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NumericUtils;

/**
 * Lucene 记录模型
 *
 * 可选字段配置参数:
 *  lucene-fieldtype    Lucene 的字段类型(string,search,stored,int,long,float,double)
 *  lucene-tokenizer
 *  lucene-char-filter
 *  lucene-token-filter
 *  lucene-find-filter
 *  lucene-query-filter
 *
 * @author Hongs
 */
public class LuceneRecord extends ModelView implements IEntity, ITrnsct, Core.Destroy {

    protected boolean IN_TRNSCT_MODE = false;
    protected boolean IN_OBJECT_MODE = false;

    private   IndexSearcher  finder  = null ;
    private   IndexReader    reader  = null ;
    private   IndexWriter    writer  = null ;
    private   String         dtpath  = null ;
    private   String         dtname  = null ;

    /**
     * 构造方法
     * @param path 存储路径
     * @param form 字段配置
     * @param tmap 类型映射
     * @param dmap 类型默认
     * @throws HongsException
     */
    public LuceneRecord(String path, Map form, Map tmap, Map dmap)
    throws HongsException {
        if (path != null) {
            Map m = new HashMap();
            m.put("CORE_PATH", Core.CORE_PATH);
            m.put("CONF_PATH", Core.CORE_PATH);
            m.put("DATA_PATH", Core.DATA_PATH);
            path = Tool.inject(path, m);
            if (! new File(path).isAbsolute()) {
                path = Core.DATA_PATH +"/lucene/"+ path;
            }
        }

        this.dtpath  = path ;
        this.setFields(form);
        this.setFtypes(tmap);
        this.setDtypes(dmap);

        // 模式标识
        CoreConfig conf = CoreConfig.getInstance();
        this.IN_TRNSCT_MODE = Synt.declare(
                Core.getInstance().got(Cnst.TRNSCT_MODE),
                conf.getProperty("core.in.trnsct.mode", false));
        this.IN_OBJECT_MODE = Synt.declare(
                Core.getInstance().got(Cnst.OBJECT_MODE),
                conf.getProperty("core.in.object.mode", false));
    }

    public LuceneRecord(String path, Map form)
    throws HongsException {
        this(path, form, null, null);
    }

    public LuceneRecord(String path)
    throws HongsException {
        this(path, null, null, null);
    }

    /**
     * 获取实例
     * 存储为 conf/form 表单为 conf.form
     * 表单缺失则尝试获取 conf/form.form
     * 实例生命周期将交由 Core 维护
     * @param conf
     * @param form
     * @return
     * @throws HongsException
     */
    public static LuceneRecord getInstance(String conf, String form) throws HongsException {
        LuceneRecord  inst;
        Core   core = Core.getInstance();
        String name = LuceneRecord.class.getName( ) + ":" +  conf + "." + form;
        if ( ! core.containsKey(name)) {
            String  canf = FormSet.hasConfFile(conf + "/" +  form)
                         ? conf + "/" + form : conf ;
            Map     farm = FormSet.getInstance(canf).getForm(name);
               inst =  new LuceneRecord(conf + "/" + form ,  farm);
               core.put( name, inst );
        } else {
               inst =  (LuceneRecord) core.got(name);
        }
        return inst;
    }

    //** 实体方法 **/

    /**
     * 获取数据
     *
     * 以下参数为特殊参数, 可在 default.properties 中配置:
     * id   ID, 仅指定单个 id 时则返回详情(info)
     * rn   行数, 明确指定为 0 则不分页
     * gn   分页
     * pn   页码
     * wd   搜索
     * ob   排序
     * rb   字段
     * or   多组"或"关系条件
     * ar   串联多组关系条件
     * sr   附加多组"或"关系, LuceneRecord 特有
     * 请注意尽量避免将其作为字段名(id,wd除外)
     *
     * @param rd
     * @return
     * @throws HongsException
     */
    @Override
    public Map retrieve(Map rd) throws HongsException {
        // 指定单个 id 则走 getOne
        Object id = rd.get (Cnst.ID_KEY);
        if (id != null && !(id instanceof Collection) && !(id instanceof Map)) {
            if ( "".equals( id ) ) {
                return  new HashMap(); // id 为空则不获取
            }
            Map  data = new HashMap();
            Map  info = getOne(rd);
            data.put("info", info);
            return data;
        }

        // 获取行数, 默认依从配置
        int rn;
        if (rd.containsKey(Cnst.RN_KEY)) {
            rn = Synt.declare(rd.get(Cnst.RN_KEY), 0);
        } else {
            rn = CoreConfig.getInstance().getProperty("fore.rows.per.page", Cnst.RN_DEF);
        }

        // 指定行数 0, 则走 getAll
        if (rn == 0) {
            Map  data = new HashMap();
            List list = getAll(rd);
            data.put("list", list);
            return data;
        }

        // 获取链数, 默认依从配置
        int gn;
        if (rd.containsKey(Cnst.GN_KEY)) {
            gn = Synt.declare(rd.get(Cnst.GN_KEY), 0);
        } else {
            gn = CoreConfig.getInstance().getProperty("fore.pags.for.page", Cnst.GN_DEF);
        }

        // 获取页码, 计算查询区间
        int pn = Synt.declare(rd.get(Cnst.PN_KEY), 1);
        if (pn < 1) pn = 1;
        if (gn < 1) gn = 1;
        int minPn = pn - (gn / 2 );
        if (minPn < 1)   minPn = 1;
        int maxPn = gn + minPn - 1;
        int limit = rn * maxPn + 1;
        int minRn = rn * (pn - 1 );
        int maxRn = rn + minRn;

        // 获取列表
        List list = getAll(rd, limit, minRn, maxRn);
        int rc = (int) list.remove(0);
        int pc = (int) Math.ceil( (double) rc / rn);

        // 记录分页
        Map  resp = new HashMap();
        Map  page = new HashMap();
        resp.put("list", list);
        resp.put("page", page);
        page.put("page", pn);
        page.put("pags", gn);
        page.put("rows", rn);
        page.put("pagecount", pc);
        page.put("rowscount", rc);
        page.put("uncertain", rc == limit); // 为 true 表示总数不确定
        if (rc == 0) {
            page.put("err", 1);
        } else
        if (list.isEmpty()) {
            page.put("err", 2);
        }

        return  resp;
    }

    /**
     * 创建记录
     * @param rd
     * @return id,name等(由dispCols指定)
     * @throws HongsException
     */
    @Override
    public Map create(Map rd) throws HongsException {
        String id = add(rd);
        Set<String> fs = getLists();
        if (fs != null && !fs.isEmpty()) {
            Map sd = new LinkedHashMap();
            sd.put(Cnst.ID_KEY, id);
            for(String  fn : getLists()) {
            if (  !  fn.contains( "." )) {
                sd.put( fn , rd.get(fn));
            }
            }
            return sd;
        } else {
            rd.put(Cnst.ID_KEY, id);
            return rd;
        }
    }

    /**
     * 更新记录
     * @param rd
     * @return
     * @throws HongsException
     */
    @Override
    public int update(Map rd) throws HongsException {
        Set<String> ids = Synt.declare(rd.get(Cnst.ID_KEY), new HashSet());
        Map         wh  = Synt.declare(rd.get(Cnst.WH_KEY), new HashMap());
        for(String  id  : ids) {
            if(!permit(wh,id)) {
                throw new HongsException(0x1096, "Can not update for id '"+id+"'");
            }
            put(id, rd  );
        }
        return ids.size();
    }

    /**
     * 删除记录
     * @param rd
     * @return
     * @throws HongsException
     */
    @Override
    public int delete(Map rd) throws HongsException {
        Set<String> ids = Synt.declare(rd.get(Cnst.ID_KEY), new HashSet());
        Map         wh  = Synt.declare(rd.get(Cnst.WH_KEY), new HashMap());
        for(String  id  : ids) {
            if(!permit(wh,id)) {
                throw new HongsException(0x1097, "Can not delete for id '"+id+"'");
            }
            del(id /**/ );
        }
        return ids.size();
    }

    /**
     * 确保操作合法
     * @param wh
     * @param id
     * @return
     * @throws HongsException
     */
    protected boolean permit(Map wh, String id) throws HongsException {
        if (id == null || "".equals(id)) {
            throw new NullPointerException("Param id for permit can not be empty");
        }
        if (wh == null) {
            throw new NullPointerException("Param wh for permit can not be null.");
        }
        Set<String> rb ;
        wh = new HashMap(wh);
        rb = new HashSet(  );
        rb.add( "id"  );
        wh.put(Cnst.ID_KEY, id);
        wh.put(Cnst.RB_KEY, rb);
        wh = getOne(wh);
        return wh != null && !wh.isEmpty();
    }

    //** 模型方法 **/

    /**
     * 添加文档
     * @param rd
     * @return ID
     * @throws HongsException
     */
    public String add(Map rd) throws HongsException {
        String id = Synt.declare(rd.get(Cnst.ID_KEY), String.class);
        if (id != null && id.length() != 0) {
            throw new HongsException.Common("Id can not set in add");
        }
        id = Core.getUniqueId();
        rd.put(Cnst.ID_KEY, id);
        addDoc(map2Doc(rd));
        return id;
    }

    /**
     * 设置文档(无则添加)
     * @param id
     * @param rd
     * @throws HongsException
     */
    public void set(String id, Map rd) throws HongsException {
        if (id == null || id.length() == 0) {
            throw new NullPointerException("Id must be set in set");
        }
        Document doc = getDoc(id);
        if (doc == null) {
            doc =  new Document();
        } else {
            /**
             * 实际运行中发现
             * 直接往取出的 doc 里设置属性, 会造成旧值的索引丢失
             * 故只好转换成 map 再重新设置, 这样才能确保索引完整
             * 但那些 Store=NO 的数据将无法设置
             */
            chkCols(new HashMap());
            Map  md = doc2Map(doc);
            md.putAll(rd);
            rd = md;
        }
        rd.put(Cnst.ID_KEY, id);
        docAdd(doc, rd);
        setDoc(id, doc);
    }

    /**
     * 修改文档(局部更新)
     * @param id
     * @param rd
     * @throws HongsException
     */
    public void put(String id, Map rd) throws HongsException {
        if (id == null || id.length() == 0) {
            throw new NullPointerException("Id must be set in put");
        }
        Document doc = getDoc(id);
        if (doc == null) {
            throw new NullPointerException("Doc#"+id+" not exists");
        } else {
            /**
             * 实际运行中发现
             * 直接往取出的 doc 里设置属性, 会造成旧值的索引丢失
             * 故只好转换成 map 再重新设置, 这样才能确保索引完整
             * 但那些 Store=NO 的数据将无法设置
             */
            chkCols(new HashMap());
            Map  md = doc2Map(doc);
            md.putAll(rd);
            rd = md;
        }
        rd.put(Cnst.ID_KEY, id);
        docAdd(doc, rd);
        setDoc(id, doc);
    }

    /**
     * 删除文档(delDoc 的别名)
     * @param id
     * @throws HongsException
     */
    public void del(String id) throws HongsException {
        if (id == null || id.length() == 0) {
            throw new NullPointerException("Id must be set in del");
        }
        Document doc = getDoc(id);
        if (doc == null) {
            throw new NullPointerException("Doc#"+id+" not exists");
        }
        delDoc(id);
    }

    /**
     * 获取文档信息
     * @param id
     * @return
     * @throws HongsException
     */
    public Map get(String id) throws HongsException {
        Document doc = getDoc(id);
        if (doc != null) {
           chkCols(new HashMap());
            return doc2Map( doc );
        } else {
            return new HashMap( );
        }
    }

    /**
     * 获取单个文档
     * @param rd
     * @return
     * @throws HongsException
     */
    public Map getOne(Map rd) throws HongsException {
        Loop roll = search(rd, 0, 1);
        if   (   roll.hasNext( )) {
            return  roll.next( );
        } else {
            return new HashMap();
        }
    }

    /**
     * 获取全部文档
     * @param rd
     * @return
     * @throws HongsException
     */
    public List getAll(Map rd) throws HongsException {
        Loop roll = search(rd, 0, 0);
        List list = new LinkedList();
        while  (  roll.hasNext()) {
            list.add(roll.next());
        }
        return list;
    }

    /**
     * 获取部分文档
     * @param rd
     * @param total 总数限制
     * @param begin 起始位置
     * @param end   结束位置(不含), 给定 0 则取到最后
     * @return      首位为实际总数, 请用 .poll() 取出
     * @throws HongsException
     */
    public List getAll(Map rd, int total, int begin, int end) throws HongsException {
        Loop roll = search(rd, begin, total - begin);
        List list = new LinkedList();
        int  idx  = 0 ;
        if ( end == 0 ) {
             end  = total - begin;
        }
        list.add( roll.size(  ) );
        while  (  roll.hasNext()) {
            list.add(roll.next());
            if (  ++idx >= end  ) {
                break ;
            }
        }
        return list;
    }

    /**
     * 搜索查询文档
     * @param rd
     * @param begin 起始位置
     * @param limit 获取限制
     * @return
     * @throws HongsException
     */
    public Loop search(Map rd, int begin, int limit) throws HongsException {
        Query q = getQuery(rd);
        Sort  s = getSort (rd);
                  chkCols (rd);
        Loop  r = new Loop(this, q, s, begin, limit);

        if (0 < Core.DEBUG && 8 != (8 & Core.DEBUG)) {
            CoreLogger.debug("LuceneRecord.search: " + r.toString());
        }

        return r ;
    }

    //** 组件方法 **/

    public void addDoc(Document doc) throws HongsException {
        IndexWriter iw = getWriter();
        try {
            iw.addDocument (doc);
        } catch (IOException ex) {
            throw new HongsException.Common(ex);
        }
        if (!IN_TRNSCT_MODE) {
            commit();
        }
    }

    public void setDoc(String id, Document doc) throws HongsException {
        IndexWriter iw = getWriter();
        try {
            iw.updateDocument (new Term(Cnst.ID_KEY, id), doc);
        } catch (IOException ex) {
            throw new HongsException.Common(ex);
        }
        if (!IN_TRNSCT_MODE) {
            commit();
        }
    }

    public void delDoc(String id) throws HongsException {
        IndexWriter iw = getWriter();
        try {
            iw.deleteDocuments(new Term(Cnst.ID_KEY, id) /**/);
        } catch (IOException ex) {
            throw new HongsException.Common(ex);
        }
        if (!IN_TRNSCT_MODE) {
            commit();
        }
    }

    public Document getDoc(String id) throws HongsException {
        IndexSearcher  ff = getFinder( );
        try {
                Query  qq = new TermQuery(new Term(Cnst.ID_KEY, id));
              TopDocs  tt = ff.search(qq,  1  );
            ScoreDoc[] hh = tt.scoreDocs;
            if  ( 0 != hh.length ) {
                return ff.doc(hh[0].doc);
            } else {
                return null;
            }
        } catch ( IOException ex ) {
            throw new HongsException.Common(ex);
        }
    }

    public Document map2Doc(Map map) throws HongsException {
        Document doc = new Document();
        docAdd(doc, map);
        return doc;
    }

    public Map doc2Map(Document doc) {
        Map map = new LinkedHashMap();
        mapAdd(map, doc);
        return map;
    }

    //** 事务方法 **/

    /**
     * 初始化读操作
     * @throws HongsException
     */
    public void initial() throws HongsException {
        if (reader != null) {
            return;
        }

        String dbpath = getDbPath();

        try {
            // 索引目录不存在则先写入一个并删除
            if (!(new File(dbpath)).exists()) {
                connect();
                del(add(new HashMap()));
                commit( );
            }

            Path p = Paths.get(dbpath );
            Directory dir = FSDirectory.open(p);

            reader = DirectoryReader.open (dir);
            finder = new IndexSearcher (reader);
        } catch (IOException x) {
            throw new HongsException.Common (x);
        }

        if (0 < Core.DEBUG && 4 != (4 & Core.DEBUG)) {
            CoreLogger.trace("Connect to lucene reader, data path: " + dbpath);
        }
    }

    /**
     * 连接写数据库
     * @throws HongsException
     */
    public void connect() throws HongsException {
        if (writer != null) {
            return;
        }

        String dbpath = getDbPath();

        try {
            IndexWriterConfig iwc = new IndexWriterConfig(getAnalyzer());
            iwc.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

            Path d = Paths.get(dbpath );
            Directory dir = FSDirectory.open(d);

            writer = new IndexWriter(dir , iwc);
        } catch (IOException x) {
            throw new HongsException.Common (x);
        }

        if (0 < Core.DEBUG && 4 != (4 & Core.DEBUG)) {
            CoreLogger.trace("Connect to lucene writer, data path: " + dbpath);
        }
    }

    /**
     * 销毁读写连接
     */
    @Override
    public void destroy() {
        if (writer != null) {
            // 默认退出时提交
            if (IN_TRNSCT_MODE) {
                try {
                    try {
                        commit();
                    } catch (Error er) {
                        rolbak();
                        throw er;
                    }
                } catch (Error e) {
                    CoreLogger.error(e);
                }
            }

            // 退出时合并索引
            try {
                writer.maybeMerge();
            } catch (IOException x) {
                CoreLogger.error(x);
            }

            try {
                writer.close();
            } catch (IOException x) {
                CoreLogger.error(x);
            } finally {
                writer = null;
            }
        }

        if (reader != null) {
            try {
                reader.close();
            } catch (IOException x) {
                CoreLogger.error(x);
            } finally {
                reader = null;
            }
        }

        if (0 < Core.DEBUG && 4 != (4 & Core.DEBUG)) {
            CoreLogger.trace("Close lucene connection, data path: " + getDbPath());
        }
    }

    /**
     * 事务开始
     */
    @Override
    public void trnsct() {
        IN_TRNSCT_MODE = true;
    }

    /**
     * 提交更改
     */
    @Override
    public void commit() {
        if (writer == null) {
            return;
        }
        IN_TRNSCT_MODE = Synt.declare(Core.getInstance().got(Cnst.TRNSCT_MODE), false);
        try {
            writer.commit(  );
        } catch (IOException ex) {
            throw new HongsError(0x3b, ex);
        }
    }

    /**
     * 回滚操作
     */
    @Override
    public void rolbak() {
        if (writer == null) {
            return;
        }
        IN_TRNSCT_MODE = Synt.declare(Core.getInstance().got(Cnst.TRNSCT_MODE), false);
        try {
            writer.rollback();
        } catch (IOException ex) {
            throw new HongsError(0x3c, ex);
        }
    }

    //** 底层方法 **/

    public String getDbPath() {
        if (null != dtpath) {
            return  dtpath;
        }
        throw new NullPointerException("DBPath can not be null");
    }

    public String getDbName() {
        if (null != dtname) {
            return  dtname;
        }
        String p = Core.DATA_PATH + "/lucene/";
        String d = getDbPath();
        if (! "/".equals (File.separator) ) {
            d = d.replace(File.separator, "/");
        }
        if (d.endsWith("/")) {
            d = d.substring(0,d.length()-1);
        }
        if (d.startsWith(p)) {
            d = d.substring(  p.length()  );
        }
        dtname= d;
        return  d;
    }

    public IndexSearcher getFinder() throws HongsException {
        initial();
        return finder;
    }

    public IndexReader getReader() throws HongsException {
        initial();
        return reader;
    }

    public IndexWriter getWriter() throws HongsException {
        connect();
        return writer;
    }

    /**
     * 查询分析
     * @param rd
     * @return
     * @throws HongsException
     */
    public Query getQuery(Map rd) throws HongsException {
        BooleanQuery query = new BooleanQuery();
        Map<String, Map> fields  =  getFields();
        Set<String  >  funcCols  =  getFuncs( );

        for (Object o : rd.entrySet()) {
            Map.Entry e = (Map.Entry) o;
            Object fv = e.getValue( );
            String fn = (String) e.getKey();

            // 功能型参数不在这里处理
            if (fn == null || fv == null
            ||  funcCols.contains( fn )) {
                continue;
            }

            Map m = (Map ) fields.get( fn );
            if (m == null) {
                continue;
            }

            IQuery aq;
            String t = getFtype(m);
            if (   "int".equals(t)) {
                aq = new IntQuery();
            } else
            if (  "long".equals(t)) {
                aq = new LongQuery();
            } else
            if ( "float".equals(t)) {
                aq = new FloatQuery();
            } else
            if ("double".equals(t)) {
                aq = new DoubleQuery();
            } else
            if ("string".equals(t)) {
                aq = new StringQuery();
            } else
            if ("search".equals(t)) {
                aq = new SearchQuery();
            } else
            {
                continue;
            }

            qryAdd(query, fn, fv, aq);
        }

        // 关键词
        if (rd.containsKey(Cnst.WD_KEY)) {
            Object fv = rd.get(Cnst.WD_KEY);

            /**
             * 当设置了多个搜索字段时
             * 将条件整理为 +(fn1:xxx fn2:xxx)
             */
            BooleanQuery quary;
            Set<String>  cols = getFinds();
            if (cols.size() < 2) {
                quary =  query;
            } else {
                quary = new BooleanQuery();
                if (! ( fv instanceof Map) && !"".equals( fv ) && fv != null) {
                    query.add(quary, BooleanClause.Occur.MUST);
                    Map fw = new HashMap();
                    fw.put(Cnst.OR_REL,fv);
                    fv= fw;
                }
            }

            for(String fk: cols) {
                qryAdd(quary, fk, fv, new SearchQuery());
            }
        }

        // 或条件
        if (rd.containsKey(Cnst.OR_KEY)) {
            BooleanQuery quary = new BooleanQuery( );
            Set<Map> set = Synt.declare(rd.get(Cnst.OR_KEY), Set.class);
            for(Map  map : set) {
                quary.add(getQuery(map), BooleanClause.Occur.SHOULD);
            }
            query.add(quary, BooleanClause.Occur.MUST);
        }

        // 附条件
        if (rd.containsKey(Cnst.SR_KEY)) {
            Set<Map> set = Synt.declare(rd.get(Cnst.SR_KEY), Set.class);
            for(Map  map : set) {
                query.add(getQuery(map), BooleanClause.Occur.SHOULD);
            }
        }

        // 并条件
        if (rd.containsKey(Cnst.AR_KEY)) {
            Set<Map> set = Synt.declare(rd.get(Cnst.AR_KEY), Set.class);
            for(Map  map : set) {
                query.add(getQuery(map), BooleanClause.Occur.MUST  );
            }
        }

        // 没有条件则查询全部
        if (query.clauses( ).isEmpty( )) {
            return new MatchAllDocsQuery();
        }

        return query;
    }

    /**
     * 排序分析
     * @param rd
     * @return
     * @throws HongsException
     */
    public Sort getSort(Map rd) throws HongsException {
        Object xb = rd.get(Cnst.OB_KEY);
        Set<String> ob = xb != null
                  ? Synt.asTerms ( xb )
                  : new LinkedHashSet();
        Map<String, Map> fields = getFields();
        List<SortField> of = new LinkedList();

        for (String fn: ob) {
            // 文档
            if (fn.equals/**/("_")) {
                of.add(SortField.FIELD_DOC  );
                continue;
            }

            // 相关
            if (fn.equals/**/("-")) {
                of.add(SortField.FIELD_SCORE);
                continue;
            }

            // 逆序
            boolean rv = fn.startsWith("-");
            if (rv) fn = fn.substring ( 1 );

            Map m = (Map ) fields.get ( fn);
            if (m == null) {
                continue;
            }
            if (sortable(m)==false) {
                continue;
            }
            if (repeated(m)== true) {
                continue;
            }

            SortField.Type st;
            String t = getFtype(m);
            if (   "int".equals(t)) {
                st = SortField.Type.INT;
            } else
            if (  "long".equals(t)) {
                st = SortField.Type.LONG;
            } else
            if ( "float".equals(t)) {
                st = SortField.Type.FLOAT;
            } else
            if ("double".equals(t)) {
                st = SortField.Type.DOUBLE;
            } else
            if ("string".equals(t)) {
                st = SortField.Type.STRING;
            } else
            {
                continue;
            }

            /**
             * 因为 Lucene 5 必须使用 DocValues 才能排序
             * 在更新数据时, 默认有加 '.' 打头的排序字段
             */
            if (st == SortField.Type.STRING) {
                of.add(new /* String */ SortField("."+fn, st, rv));
            } else {
                of.add(new SortedNumericSortField("."+fn, st, rv));
            }
        }

        // 未指定则按文档顺序
        if (of.isEmpty()) {
            of.add(SortField.FIELD_DOC);
        }

        return new Sort(of.toArray(new SortField[0]));
    }

    /**
     * 返回字段
     * @param rd
     */
    public void chkCols(Map rd) {
        Object fz = rd.get(Cnst.RB_KEY);
        Set<String> fs = fz != null
                  ? Synt.asTerms ( fz )
                  : new LinkedHashSet();
        Map<String, Map> fields = getFields();

        if (fs != null && !fs.isEmpty()) {
            Set<String> cf = new HashSet();
            Set<String> sf = new HashSet();
            for (String fn : fs) {
                if (fn.startsWith("-")) {
                    fn= fn.substring(1);
                    cf.add(fn);
                } else {
                    sf.add(fn);
                }
            }
            if (!sf.isEmpty()) {
                cf.addAll(fields.keySet());
                cf.removeAll(sf);
            }
            cf.add("@"); // Skip form conf;

            for(Map.Entry<String, Map> me : fields.entrySet()) {
                Map fc = me.getValue();
                String f = me.getKey();
                fc.put   ( "--unwanted--" , cf.contains(f) || unstored(fc));
            }
        } else {
            for(Map.Entry<String, Map> me : fields.entrySet()) {
                Map fc = me.getValue();
                fc.remove( "--unwanted--" );
            }
        }
    }

    //** 底层工具 **/

    /**
     * 写入分析器
     * @return
     * @throws HongsException
     */
    protected Analyzer getAnalyzer() throws HongsException {
        Map<String, Analyzer> az = new HashMap();
        Map<String, Map>  fields = getFields(  );
        Analyzer ad = new StandardAnalyzer();
        for(Object ot : fields.entrySet( ) ) {
            Map.Entry et = (Map.Entry) ot;
            String fn = (String) et.getKey();
            Map    fc = (Map ) et.getValue();
            String t = getFtype(fc);
            if ("search".equals(t)) {
                az.put(fn, getAnalyzer(fc, false));
            }
        }
        return new PerFieldAnalyzerWrapper(ad, az);
    }

    /**
     * 构建分析器
     * @param fc 字段配置
     * @param iq 是否查询
     * @return
     * @throws HongsException
     */
    protected Analyzer getAnalyzer(Map fc, boolean iq) throws HongsException {
        try {
            CustomAnalyzer.Builder cb = CustomAnalyzer.builder();
            String kn, an, ac; Map oc;

            // 分词器
            an = Synt.declare(fc.get("lucene-tokenizer"), "");
            if (!"".equals(an)) {
                int p  = an.indexOf('{');
                if (p != -1) {
                    ac = an.substring(p);
                    an = an.substring(0, p - 1).trim( );
                    oc = Synt.declare(Data.toObject(ac), Map.class);
                    cb.withTokenizer(an, oc);
                } else {
                    cb.withTokenizer(an/**/);
                }
            } else {
                cb.withTokenizer("Standard");
            }

            // 过滤器
            for(Object ot2 : fc.entrySet()) {
                Map.Entry et2 = (Map.Entry) ot2;
                kn = (String) et2.getKey();
                if (iq) {
                    if (kn.startsWith("lucene-find-filter")) {
                        an = (String) et2.getValue();
                        an = an.trim();
                        if ("".equals(an)) {
                            continue;
                        }
                        int p  = an.indexOf('{');
                        if (p != -1) {
                            ac = an.substring(p);
                            an = an.substring(0, p - 1).trim( );
                            oc = Synt.declare(Data.toObject(ac), Map.class);
                            cb.addCharFilter(an, oc);
                        } else {
                            cb.addCharFilter(an/**/);
                        }
                    } else
                    if (kn.startsWith("lucene-query-filter")) {
                        an = (String) et2.getValue();
                        an = an.trim();
                        if ("".equals(an)) {
                            continue;
                        }
                        int p  = an.indexOf('{');
                        if (p != -1) {
                            ac = an.substring(p);
                            an = an.substring(0, p - 1).trim( );
                            oc = Synt.declare(Data.toObject(ac), Map.class);
                            cb.addTokenFilter(an, oc);
                        } else {
                            cb.addTokenFilter(an/**/);
                        }
                    }
                } else {
                    if (kn.startsWith("lucene-char-filter")) {
                        an = (String) et2.getValue();
                        an = an.trim();
                        if ("".equals(an)) {
                            continue;
                        }
                        int p  = an.indexOf('{');
                        if (p != -1) {
                            ac = an.substring(p);
                            an = an.substring(0, p - 1).trim();
                            oc = Synt.declare(Data.toObject(ac), Map.class);
                            cb.addCharFilter(an, oc);
                        } else {
                            cb.addCharFilter(an/**/);
                        }
                    } else
                    if (kn.startsWith("lucene-token-filter")) {
                        an = (String) et2.getValue();
                        an = an.trim();
                        if ("".equals(an)) {
                            continue;
                        }
                        int p  = an.indexOf('{');
                        if (p != -1) {
                            ac = an.substring(p);
                            an = an.substring(0, p - 1).trim();
                            oc = Synt.declare(Data.toObject(ac), Map.class);
                            cb.addTokenFilter(an, oc);
                        } else {
                            cb.addTokenFilter(an/**/);
                        }
                    }
                }
            }

            return cb.build();
        } catch (IOException ex) {
            throw new HongsException.Common(ex);
        } catch ( IllegalArgumentException  ex) {
            throw new HongsException.Common(ex);
        }
    }

    /**
     * 获取字段类型
     * 支持的类型有
     * int
     * long
     * float
     * double
     * string
     * text
     * json
     * @param fc 字段配置
     * @return
     */
    protected String getFtype(Map fc) {
        String t = Synt.declare(fc.get("lucene-fieldtype"), String.class);

        // 如果未指定 lucene-fieldtype 则用 __type__ 替代
        if (t == null) {
            t = (String) fc.get("__type__");

            if ("search".equals(t)
            ||  "stored".equals(t)
            ||  "sorted".equals(t)) {
                return t;
            }

            // 特例处理
            if ("textarea".equals(t)) {
                return "stored";
            }
            if ("textcase".equals(t)) {
                return "search";
            }

            // 类型细分
            t = Synt.declare(getFtypes().get(t), t);
            if ("number".equals(t)) {
                t = Synt.declare(fc.get("type"), "double");
            } else
            if ( "date" .equals(t)) {
                Object x = fc.get("type");
                if ("microtime".equals(x) || "timestamp".equals(x)) {
                    t = "long";
                }
            }
        } else
        if (t.equals("number")) {
            t = "double";
        } else
        if (t.equals( "text" )) {
            t = "search";
        }

        return t;
    }

    protected boolean repeated(Map fc) {
        return Synt.asserts(fc.get("__repeated__"), false);
    }

    protected boolean unwanted(Map fc) {
        return Synt.asserts(fc.get("--unwanted--"), false);
    }

    protected boolean unstored(Map fc) {
        return Synt.asserts(fc.get(  "unstored"  ), false);
    }

    protected void mapAdd(Map map, Document doc) {
        Map<String, Map> fields = getFields( );
        for(Object o : fields.entrySet()) {
            Map.Entry e = (Map.Entry) o;
            Map    m = (Map) e.getValue();
            String k = (String)e.getKey();

            if (unstored(m)
            ||  unwanted(m)) {
                continue;
            }

            String  t = getFtype(m);
            boolean r = repeated(m);
            IndexableField[] fs = doc.getFields(k);

            if (   "int".equals(t)
            ||    "long".equals(t)
            ||   "float".equals(t)
            ||  "double".equals(t)
            ||  "number".equals(t)) {
            if ( ! IN_OBJECT_MODE ) {
                if (r) {
                    if (fs.length > 0) {
                        for(IndexableField f : fs) {
                            Dict.put(map , Tool.toNumStr(f.numericValue()), k, null);
                        }
                    } else {
                        map.put(k, new ArrayList());
                    }
                } else {
                    if (fs.length > 0) {
                        map.put(k, Tool.toNumStr(fs[ 0 ].numericValue( )));
                    } else {
                        map.put(k,"0");
                    }
                }
            } else {
                if (r) {
                    if (fs.length > 0) {
                        for(IndexableField f : fs) {
                            Dict.put(map , f.numericValue(), k, null);
                        }
                    } else {
                        map.put(k, new ArrayList());
                    }
                } else {
                    if (fs.length > 0) {
                        map.put(k, fs[ 0 ].numericValue( ));
                    } else {
                        map.put(k, 0 );
                    }
                }
            }
            } else
            if (  "date".equals(t)) {
            if ( ! IN_OBJECT_MODE ) {
                String           fmt = CoreLocale.getInstance( )
                           .getProperty("core.default.datetime");
                SimpleDateFormat sdf = new SimpleDateFormat(fmt);
                if (r) {
                    if (fs.length > 0) {
                        for(IndexableField f : fs) {
                            Dict.put(map , sdf.format(new Date(f.numericValue().longValue())), k, null);
                        }
                    } else {
                        map.put(k, new ArrayList());
                    }
                } else {
                    if (fs.length > 0) {
                        map.put(k, sdf.format(new Date(fs[ 0 ].numericValue( ).longValue())));
                    } else {
                        map.put(k, null);
                    }
                }
            } else {
                if (r) {
                    if (fs.length > 0) {
                        for(IndexableField f : fs) {
                            Dict.put(map , new Date(f.numericValue().longValue()), k, null);
                        }
                    } else {
                        map.put(k, new ArrayList());
                    }
                } else {
                    if (fs.length > 0) {
                        map.put(k, new Date(fs[ 0 ].numericValue( ).longValue()));
                    } else {
                        map.put(k, null);
                    }
                }
            }
            }
            if (  "json".equals(t)) {
                if (r) {
                    if (fs.length > 0) {
                        for(IndexableField f : fs) {
                            Dict.put(map , Data.toObject(f.stringValue()), k, null);
                        }
                    } else {
                        map.put(k, new ArrayList());
                    }
                } else {
                    if (fs.length > 0) {
                        map.put(k, Data.toObject(fs[ 0 ].stringValue( )));
                    } else {
                        map.put(k, new HashMap( ) );
                    }
                }
            } else
            {
                if (r) {
                    if (fs.length > 0) {
                        for(IndexableField f : fs) {
                            Dict.put(map , f.stringValue(), k, null);
                        }
                    } else {
                        map.put(k, new ArrayList());
                    }
                } else {
                    if (fs.length > 0) {
                        map.put(k, fs[ 0 ].stringValue( ));
                    } else {
                        map.put(k, "");
                    }
                }
            }
        }
    }

    protected void docAdd(Document doc, Map map) {
        Map<String, Map> fields = getFields();
        for(Object o : fields.entrySet()) {
            Map.Entry e = (Map.Entry) o;
            Map    m = (Map) e.getValue();
            String k = (String)e.getKey();
            Object v = Dict.getParam(map , k);

            if (null == v
            ||  "".equals(k)
            || "@".equals(k)
            || "Ignore".equals(m.get("rule"))) {
                continue;
            }

            String  t = getFtype(m);
            boolean s = sortable(m);
            boolean u = unstored(m);
            boolean r = repeated(m);

            doc.removeFields(k);
            if (r && v instanceof Collection) {
                for (Object x : ( Collection) v) {
                    this.docAdd(doc, k, x, t, s, u, true );
                }
            } else
            if (r && v instanceof Object[ ] ) {
                for (Object x : ( Object[ ] ) v) {
                    this.docAdd(doc, k, x, t, s, u, true );
                }
            } else
            if (r) {
                Set a = Synt.declare(v, Set.class);
                for (Object x : a) {
                    this.docAdd(doc, k, x, t, s, u, true );
                }
            } else
            {
                /**/this.docAdd(doc, k, v, t, s, u, false);
            }
        }
    }

    /**
     * 添加属性取值到文档
     * @param doc 文档对象
     * @param k 属性
     * @param v 取值
     * @param t 类型
     * @param s 是否可排序
     * @param u 是否不存储
     * @param r 是否多个值
     */
    protected void docAdd(Document doc, String k, Object v, String t, boolean s, boolean u, boolean r) {
        if (   "int".equals(t)) {
            doc.add(new    IntField(k, Synt.declare(v, 0 ), u ? Field.Store.NO : Field.Store.YES));
        } else
        if (  "long".equals(t)) {
            doc.add(new   LongField(k, Synt.declare(v, 0L), u ? Field.Store.NO : Field.Store.YES));
        } else
        if ( "float".equals(t)) {
            doc.add(new  FloatField(k, Synt.declare(v, 0.0F), u ? Field.Store.NO : Field.Store.YES));
        } else
        if ("double".equals(t)) {
            doc.add(new DoubleField(k, Synt.declare(v, 0.0D), u ? Field.Store.NO : Field.Store.YES));
        } else
        if ("string".equals(t)) {
            doc.add(new StringField(k, Synt.declare(v, ""), u ? Field.Store.NO : Field.Store.YES));
        } else
        if ("search".equals(t)) {
            doc.add(new   TextField(k, Synt.declare(v, ""), u ? Field.Store.NO : Field.Store.YES));
        } else
        if ("sorted".equals(t)) {
            s = true; // 此类型的字段仅用于排序
        } else
        if (  "date".equals(t)) {
            if (v instanceof Date) {
                v = ((Date) v).getTime( );
            }
            doc.add(new   LongField(k, Synt.declare(v, 0L), u ? Field.Store.NO : Field.Store.YES));
        }
        if (  "json".equals(t)) {
            if (v == null || "".equals(v)) {
                v = "{}";
            } else
            if (! ( v instanceof String )) {
                v = Data.toString(v);
            }
            doc.add(new StoredField(k, ( String ) v));
        } else
        {
            doc.add(new StoredField(k, v.toString()));
        }

        /**
         * 针对 Lucene 5 的排序
         * 多值的字段只取第一个
         */
        if (s) {
            if (r) {
                if (doc.getField(k) != null) {
                    return;
                }
            }
            if (   "int".equals(t)) {
                doc.add(new SortedNumericDocValuesField("."+k, Synt.declare(v, 0L)));
            } else
            if (  "long".equals(t)) {
                doc.add(new SortedNumericDocValuesField("."+k, Synt.declare(v, 0L)));
            } else
            if ( "float".equals(t)) {
                doc.add(new SortedNumericDocValuesField("."+k, NumericUtils. floatToSortableInt (Synt.declare(v, 0.0F))));
            } else
            if ("double".equals(t)) {
                doc.add(new SortedNumericDocValuesField("."+k, NumericUtils.doubleToSortableLong(Synt.declare(v, 0.0D))));
            } else
            {
                doc.add(new SortedDocValuesField("."+k, new BytesRef(Synt.declare(v, ""))));
            }
        }
    }

    /**
     * 组织查询条件
     *
     * 操作符:
     * !eq 等于
     * !ne 不等于
     * !lt 小于
     * !le 小于或等于
     * !gt 大于
     * !ge 大于或等于
     * !in 包含
     * !ni 不包含
     * 以下为 Lucene 特有的操作符:
     * !or 或匹配, 有则优先
     * !oi 或包含, 有则优先
     * !ai 全包含, 此为目标真子集
     * !wt 优先度, 设定查询的权重
     * 注意: 默认情况下查询参数不给值则忽略, 如果指定了操作符则匹配空串
     *
     * @param qry
     * @param k
     * @param v
     * @param q
     * @throws HongsException
     */
    protected void qryAdd(BooleanQuery qry, String k, Object v, IQuery q)
    throws HongsException {
        Map m;
        if (v instanceof Map) {
            m = new HashMap();
            m.putAll((Map) v);
        } else {
            if (null==v || "".equals(v)) {
                return ;
            }
            m = new HashMap();
            if (v instanceof Collection) {
                Collection c = (Collection) v;
                    c.remove("");
                if (c.isEmpty()) {
                    return;
                }
                m.put(Cnst.IN_REL, c);
            } else
            {
                m.put(Cnst.EQ_REL, v);
            }
        }

        // 对 text 类型指定分词器
        if (q instanceof SearchQuery) {
            Map<String, Map> fields = getFields();
            SearchQuery sq = (SearchQuery) q;
            Map fc = (Map) fields.get(k);
            sq.ana(getAnalyzer(fc,true));

            // 额外的一些细微配置
            sq.phraseSlop (Synt.declare(fc.get("lucene-parser-phraseSlop" ), Integer.class));
            sq.fuzzyPreLen(Synt.declare(fc.get("lucene-parser-fuzzyPreLen"), Integer.class));
            sq.fuzzyMinSim(Synt.declare(fc.get("lucene-parser-fuzzyMinSim"),   Float.class));
            sq.advanceAnalysisInUse(Synt.declare(fc.get("lucene-parser-advanceAnalysisInUse"), Boolean.class));
            sq.defaultOperatorIsAnd(Synt.declare(fc.get("lucene-parser-defaultOperatorIsAnd"), Boolean.class));
            sq.allowLeadingWildcard(Synt.declare(fc.get("lucene-parser-allowLeadingWildcard"), Boolean.class));
            sq.lowercaseExpandedTerms(Synt.declare(fc.get("lucene-parser-lowercaseExpandedTerms"), Boolean.class));
            sq.enablePositionIncrements(Synt.declare(fc.get("lucene-parser-enablePositionIncrements"), Boolean.class));
        }

        if (m.containsKey(Cnst.WT_REL)) {
            Object n = m.remove(Cnst.WT_REL);
            q.bst( Synt.declare(n, 1F));
        }

        if (m.containsKey(Cnst.EQ_REL)) {
            Object n = m.remove(Cnst.EQ_REL);
            qry.add(q.add(k, n), BooleanClause.Occur.MUST);
        }

        if (m.containsKey(Cnst.NE_REL)) {
            Object n = m.remove(Cnst.NE_REL);
            qry.add(q.add(k, n), BooleanClause.Occur.MUST_NOT);
        }

        if (m.containsKey(Cnst.OR_REL)) {
            Object n = m.remove(Cnst.OR_REL);
            qry.add(q.add(k, n), BooleanClause.Occur.SHOULD);
        }

        if (m.containsKey(Cnst.IN_REL)) { // In
            BooleanQuery qay = new BooleanQuery();
            Set a = Synt.declare(m.remove(Cnst.IN_REL), new HashSet());
            for(Object x : a) {
                qay.add(q.add(k, x), BooleanClause.Occur.SHOULD);
            }
            qry.add(qay, BooleanClause.Occur.MUST);
        }

        if (m.containsKey(Cnst.AI_REL)) { // All In
            Set a = Synt.declare(m.remove(Cnst.AI_REL), new HashSet());
            for(Object x : a) {
                qry.add(q.add(k, x), BooleanClause.Occur.MUST);
            }
        }

        if (m.containsKey(Cnst.NI_REL)) { // Not In
            Set a = Synt.declare(m.remove(Cnst.NI_REL), new HashSet());
            for(Object x : a) {
                qry.add(q.add(k, x), BooleanClause.Occur.MUST_NOT);
            }
        }

        if (m.containsKey(Cnst.OI_REL)) { // Or In
            Set a = Synt.declare(m.remove(Cnst.OI_REL), new HashSet());
            for(Object x : a) {
                qry.add(q.add(k, x), BooleanClause.Occur.SHOULD);
            }
        }

        //** 区间查询 **/

        Object  n, x;
        boolean l, g;

        if (m.containsKey(Cnst.GT_REL)) {
            n = m.remove (Cnst.GT_REL); l = false;
        } else
        if (m.containsKey(Cnst.GE_REL)) {
            n = m.remove (Cnst.GE_REL); l = true;
        } else
        {
            n = null; l = true;
        }

        if (m.containsKey(Cnst.LT_REL)) {
            x = m.remove (Cnst.LT_REL); g = false;
        } else
        if (m.containsKey(Cnst.LE_REL)) {
            x = m.remove (Cnst.LE_REL); g = true;
        } else
        {
            x = null; g = true;
        }

        if (n != null || x != null) {
            qry.add(q.add(k, n, x, l, g), BooleanClause.Occur.MUST);
        }

        //** 其他查询 **/

        if (!m.isEmpty()) {
            Set s = new HashSet();
            s.addAll(m.values( ));
            qryAdd(qry, k, s, q );
        }
    }

    //** 辅助对象 **/

    /**
     * 查询迭代器
     */
    public static class Loop implements Iterator<Map> {
        private final IndexSearcher finder;
        private final IndexReader   reader;
        private final LuceneRecord  that;
        private       ScoreDoc[]    docs;
        private       ScoreDoc      doc ;
        private final Query   q;
        private final Sort    s;
        private final int     b; // 起始位置
        private final int     l; // 单次限制
        private       int     L; // 起始限制
        private       int     h; // 单次总数
        private       int     H; // 全局总数
        private       int     i; // 提取游标
        private       boolean A; // 无限查询

        /**
         * 查询迭代器
         * @param that 记录实例
         * @param q 查询对象
         * @param s 排序对象
         * @param l 查询限额
         * @param b 起始偏移
         */
        public Loop(LuceneRecord that, Query q, Sort s, int b, int l) {
            this.that = that;
            this.docs = null;
            this.doc  = null;
            this.q    = q;
            this.s    = s;
            this.b    = b;

            // 是否获取全部
            if ( l  ==  0) {
                 l = 1000;
                 A = true;
            }

            this.l    = l;
            this.L    = l;

            // 起始位置偏移
            if ( b  !=  0) {
                 L  +=  b;
            }

            // 获取查读对象
            try {
                finder = that.getFinder();
                reader = that.getReader();
            } catch ( HongsException ex ) {
                throw ex.toUnchecked(   );
            }
        }

        @Override
        public boolean hasNext() {
            try {
                if ( docs == null) {
                     TopDocs tops;
                    if (s != null) {
                        tops = finder.search/***/(/***/q, L, s);
                    } else {
                        tops = finder.search/***/(/***/q, L);
                    }
                    docs = tops.scoreDocs;
                    h    = docs.length;
                    i    = b;
                    H    = h;
                    L    = l;
                } else
                if ( A && L <= i ) {
                     TopDocs tops;
                    if (s != null) {
                        tops = finder.searchAfter(doc, q, l, s);
                    } else {
                        tops = finder.searchAfter(doc, q, l);
                    }
                    docs = tops.scoreDocs;
                    h    = docs.length;
                    i    = 0;
                    H   += h;
                }
                return i < h;
            } catch (IOException ex) {
                throw new HongsUnchecked.Common(ex);
            }
        }

        @Override
        public Map next() {
            if ( i >= h ) {
                throw new NullPointerException("hasNext not run ?");
            }
            try {
                /*Read*/ doc = docs[i++];
                Document dox = reader.document( doc.doc );
                return that.doc2Map(dox);
            } catch (IOException ex) {
                throw new HongsUnchecked.Common(ex);
            }
        }

        /**
         * 获取命中总数
         * 注意:
         * 初始化时 l 参数为 0 (即获取全部命中)
         * 则在全部循环完后获取到的数值才是对的
         * 但其实此时完全可以直接计算循环的次数
         * 此方法主要用于分页时获取查询命中总量
         * @return
         */
        public int size() {
            hasNext();
            return H ;
        }

        @Override
        public String toString() {
            hasNext();
            StringBuilder sb = new StringBuilder(q.toString());
            if ( s != null ) {
                sb.append(" Sort: " );
                sb.append( s );
            }
            if ( l != 0 || b != 0 ) {
                sb.append(" Limit: ");
                sb.append( b );
                sb.append(",");
                sb.append( l );
            }
            return sb.toString();
        }
    }

    protected static interface IQuery {
        public void  bst(float  w);
        public Query add(String k, Object v);
        public Query add(String k, Object n, Object x, boolean l, boolean r);
    }

    protected static class IntQuery implements IQuery {
        private Float w = null;
        @Override
        public void  bst(float  w) {
            this.w = w;
        }
        @Override
        public Query add(String k, Object v) {
            Integer n2 = Synt.declare(v, Integer.class);
            Query   q2 = NumericRangeQuery.newIntRange(k, n2, n2, true, true);
            if (w != null) q2.setBoost(w);
            return  q2;
        }
        @Override
        public Query add(String k, Object n, Object x, boolean l, boolean g) {
            Integer n2 = Synt.declare(n, Integer.class);
            Integer x2 = Synt.declare(x, Integer.class);
            Query   q2 = NumericRangeQuery.newIntRange(k, n2, x2, l, g);
            if (w != null) q2.setBoost(w);
            return  q2;
        }
    }

    protected static class LongQuery implements IQuery {
        private Float w = null;
        @Override
        public void  bst(float  w) {
            this.w = w;
        }
        @Override
        public Query add(String k, Object v) {
            Long    n2 = Synt.declare(v, Long.class);
            Query   q2 = NumericRangeQuery.newLongRange(k, n2, n2, true, true);
            if (w != null) q2.setBoost(w);
            return  q2;
        }
        @Override
        public Query add(String k, Object n, Object x, boolean l, boolean g) {
            Long    n2 = Synt.declare(n, Long.class);
            Long    x2 = Synt.declare(x, Long.class);
            Query   q2 = NumericRangeQuery.newLongRange(k, n2, x2, l, g);
            if (w != null) q2.setBoost(w);
            return  q2;
        }
    }

    protected static class FloatQuery implements IQuery {
        private Float w = null;
        @Override
        public void  bst(float  w) {
            this.w = w;
        }
        @Override
        public Query add(String k, Object v) {
            Float   n2 = Synt.declare(v, Float.class);
            Query   q2 = NumericRangeQuery.newFloatRange(k, n2, n2, true, true);
            if (w != null) q2.setBoost(w);
            return  q2;
        }
        @Override
        public Query add(String k, Object n, Object x, boolean l, boolean g) {
            Float   n2 = Synt.declare(n, Float.class);
            Float   x2 = Synt.declare(x, Float.class);
            Query   q2 = NumericRangeQuery.newFloatRange(k, n2, x2, l, g);
            if (w != null) q2.setBoost(w);
            return  q2;
        }
    }

    protected static class DoubleQuery implements IQuery {
        private Float w = null;
        @Override
        public void  bst(float  w) {
            this.w = w;
        }
        @Override
        public Query add(String k, Object v) {
            Double  n2 = Synt.declare(v, Double.class);
            Query   q2 = NumericRangeQuery.newDoubleRange(k, n2, n2, true, true);
            if (w != null) q2.setBoost(w);
            return  q2;
        }
        @Override
        public Query add(String k, Object n, Object x, boolean l, boolean g) {
            Double  n2 = Synt.declare(n, Double.class);
            Double  x2 = Synt.declare(x, Double.class);
            Query   q2 = NumericRangeQuery.newDoubleRange(k, n2, x2, l, g);
            if (w != null) q2.setBoost(w);
            return  q2;
        }
    }

    protected static class StringQuery implements IQuery {
        private Float w = null;
        @Override
        public void  bst(float  w) {
            this.w = w;
        }
        @Override
        public Query add(String k, Object v) {
            Query   q2 = new TermQuery(new Term(k, v.toString()));
            if (w != null) q2.setBoost(w);
            return  q2;
        }
        @Override
        public Query add(String k, Object n, Object x, boolean l, boolean g) {
            String  n2 = n.toString();
            String  x2 = x.toString();
            Query   q2 = TermRangeQuery.newStringRange(k, n2, x2, l, g);
            if (w != null) q2.setBoost(w);
            return  q2;
        }
    }

    protected static class SearchQuery implements IQuery {
        private Boolean  des = null;
        private Boolean  and = null;
        private Boolean  alw = null;
        private Boolean  let = null;
        private Boolean  epi = null;
        private Integer  phr = null;
        private Integer  fpl = null;
        private Float    fms = null;
        public void  advanceAnalysisInUse(Boolean x) {
            this.des = x;
        }
        public void  defaultOperatorIsAnd(Boolean x) {
            this.and = x;
        }
        public void  allowLeadingWildcard(Boolean x) {
            this.alw = x;
        }
        public void  lowercaseExpandedTerms(Boolean x) {
            this.let = x;
        }
        public void  enablePositionIncrements(Boolean x) {
            this.epi = x;
        }
        public void  phraseSlop (Integer x) {
            this.phr = x;
        }
        public void  fuzzyPreLen(Integer x) {
            this.fpl = x;
        }
        public void  fuzzyMinSim(Float   x) {
            this.fms = x;
        }

        private Analyzer a = null;
        private Float    w = null;
        public void  ana(Analyzer a) {
            this.a = a;
        }
        @Override
        public void  bst(  float  w) {
            this.w = w;
        }
        @Override
        public Query add(String k, Object v) {
            try {
                QueryParser qp = new QueryParser(k , a);

                String s = v.toString( );
                if (des == null || !des) {
                    s = QueryParser.escape(s);
                }
                if (and != null &&  and) {
                    qp.setDefaultOperator (QueryParser.AND_OPERATOR);
                }
                if (epi != null) qp.setEnablePositionIncrements(epi);
                if (let != null) qp.setLowercaseExpandedTerms(let);
                if (alw != null) qp.setAllowLeadingWildcard(alw);
                if (fpl != null) qp.setFuzzyPrefixLength(fpl);
                if (fms != null) qp.setFuzzyMinSim      (fms);
                if (phr != null) qp.setPhraseSlop       (phr);

                Query  q2 = qp.parse(s);
                if ( w  != null) q2.setBoost(w);
                return q2 ;
            } catch (ParseException ex) {
                throw new HongsUnchecked.Common(ex);
            }
        }
        @Override
        public Query add(String k, Object n, Object x, boolean l, boolean g) {
            String  n2 = n.toString();
            String  x2 = x.toString();
            Query   q2 = TermRangeQuery.newStringRange(k, n2, x2, l, g);
            if (w != null) q2.setBoost(w);
            return  q2;
        }
    }

}
