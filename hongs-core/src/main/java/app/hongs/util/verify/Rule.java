package app.hongs.util.verify;

import app.hongs.HongsException;
import java.util.Map;

/**
 * 规则基类
 * @author Hongs
 */
public abstract class Rule {
    /**
     * 返回此对象将被抛弃
     */
    public static final Object BLANK = new Object();

    public Map  params = null;
    public Map  values = null;
    public Veri helper ;

    public void setParams(Map  params) {
        this.params = params;
    }
    public void setValues(Map  values) {
        this.values = values;
    }
    public void setHelper(Veri helper) {
        this.helper = helper;
    }

    public abstract Object verify(Object value) throws Wrong, Wrongs, HongsException;
}
