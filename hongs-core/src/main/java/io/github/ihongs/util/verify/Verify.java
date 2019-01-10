package io.github.ihongs.util.verify;

import io.github.ihongs.HongsException;
import io.github.ihongs.util.Dict;
import io.github.ihongs.util.Synt;
import static io.github.ihongs.util.verify.Rule.BLANK;
import static io.github.ihongs.util.verify.Rule.BREAK;
import java.util.Collection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * 数据校验助手
 * @author Hongs
 *
 * <p>Java8 中利用 Ruly 使用函数式, 可简化代码, 如:</p>
 * <pre>
 *  values = new Verify()
 *      .addRule("f1", (v, r)->{
 *          return v != null ? v : Rule.BLANK;
 *      })
 *      .addRule("f2", (v, r)->{
 *          return v != null ? v : Rule.EMPTY;
 *      })
 *      .verify(values);
 * </pre>
 *
 * <h3>异常代码</h3>
 * <pre>
 * 代码区间 0x10f0~0x10ff
 * Ex10f0=规则格式错误
 * </pre>
 */
public class Verify implements Veri {

    private final Map<String, List<Ruly>> rules;
    private boolean update;
    private boolean prompt;

    public Verify() {
        rules = new LinkedHashMap();
    }

    /**
     * 获取规则
     * @return
     */
    public Map<String, List<Ruly>> getRules() {
        return rules ;
    }

    /**
     * 设置规则
     * @param name
     * @param rule
     * @return
     */
    public Verify setRule(String name, Ruly... rule) {
        rules.put(name , Arrays.asList(rule));
        return this;
    }

    /**
     * 添加规则
     * @param name
     * @param rule
     * @return
     */
    public Verify addRule(String name, Ruly... rule) {
        List rulez = rules.get(name);
        if (rulez == null) {
            rulez =  new ArrayList();
            rules.put( name, rulez );
        }
        rulez.addAll(Arrays.asList(rule));
        return this;
    }

    @Override
    public boolean isUpdate() {
        return update;
    }
    @Override
    public boolean isPrompt() {
        return prompt;
    }
    @Override
    public void isUpdate(boolean update) {
        this.update = update;
    }
    @Override
    public void isPrompt(boolean prompt) {
        this.prompt = prompt;
    }

    /**
     * 校验数据
     * @param values
     * @return
     * @throws Wrongs
     * @throws HongsException
     */
    @Override
    public Map verify(Map values) throws Wrongs, HongsException {
        Map<String, Wrong > wrongz = new LinkedHashMap();
        Map<String, Object> cleans = new LinkedHashMap();

        if (values == null) {
            values =  new HashMap();
        }

        for(Map.Entry<String , List<Ruly>> et : rules.entrySet()) {
            List<Ruly> rulez = et.getValue();
            String     name  = et.getKey(  );
            Object     data  = Dict.get(values, BLANK, Dict.splitKeys(name));

            data = verify(values, cleans, wrongz, data, name, rulez);

            if (prompt && ! wrongz.isEmpty()) {
                break;
            } else
            if (data == BREAK) {
                break;
            } else
            if (data == BLANK) {
                continue;
            }

            Dict.setParam(cleans, data, name);
        }

        if (!wrongz.isEmpty()) {
            throw new Wrongs(wrongz);
        }

        return cleans;
    }

    private Object verify(Map values, Map cleans, Map wrongz, Object data, String name, List<Ruly> rulez) throws HongsException {
        int i =0;
        for(Ruly rule : rulez) {
            i ++;

            Verity veri = new Verity(this, values, cleans, data != BLANK);

            try {
                data = rule.verify(! veri.isValued() ? null : data, veri);
            } catch (Wrong  w) {
                // 设置字段标签
                if (w.getLocalizedCaption( ) == null) {
                    if (rule instanceof Rule) {
                        Rule r = (Rule) rule ;
                        w.setLocalizedCaption(Synt.defxult(
                                Synt.asString(r.getParam("__text__")),
                                Synt.asString(r.getParam("__name__")),
                                              name) );
                    } else {
                        w.setLocalizedCaption(name);
                    }
                }
                failed(wrongz, w , name);
                data =  BLANK;
                break;
            } catch (Wrongs w) {
                failed(wrongz, w , name);
                data =  BLANK;
                break;
            }
            if (data == BLANK) {
                break;
            }
            if (data == BREAK) {
                break;
            }

            if (rule instanceof Repeated) {
                List<Ruly> rulex = rulez.subList(i, rulez.size());
                data = verify(values, cleans, wrongz, data, name, rulex, (Repeated) rule);
                break;
            }
        }
        return  data ;
    }

    private Object verify(Map values, Map cleans, Map wrongz, Object data, String name, List<Ruly> rulez, Repeated rule)
    throws HongsException {
        Collection data2 = rule.getContext();
        Collection skips = rule.getDefiant();

        // 将后面的规则应用于每一个值
        if (data instanceof Collection) {
            int i3 = -1;
            for(Object data3 :  ( Collection )  data  ) {
                i3 += 1;

                if (data3 == null || skips.contains(data3)) {
                    continue;
                }

                String name3 = name + "[" + i3 + "]";
                data3 = verify(values, cleans, wrongz, data3, name3, rulez);
                if (data3 !=  BLANK) {
                    data2.add(data3);
                } else if (prompt && !wrongz.isEmpty()) {
                    return BLANK;
                }
            }
        } else if (data instanceof Map) {
            for(Object i3 : ( ( Map ) data).entrySet()) {
                Map.Entry e3 = (Map.Entry) i3;
                Object data3 = e3.getValue( );

                if (data3 == null || skips.contains(data3)) {
                    continue;
                }

                String name3 = name + "." + e3.getKey();
                data3 = verify(values, cleans, wrongz, data3, name3, rulez);
                if (data3 !=  BLANK) {
                    data2.add(data3);
                } else if (prompt && !wrongz.isEmpty()) {
                    return BLANK;
                }
            }
        }

        // 完成后还需再次校验一下结果
        try {
            return rule.remedy(data2);
        } catch (Wrong  w) {
            if (rule instanceof Rule) {
                Rule r = (Rule) rule ;
                w.setLocalizedCaption(Synt.defxult(
                        Synt.asString(r.getParam("__text__")),
                        Synt.asString(r.getParam("__name__")),
                                      name) );
            } else {
                w.setLocalizedCaption(name);
            }
            failed(wrongz, w , name );
            return BLANK ;
        }
    }

    public static void failed(Map<String, Wrong> wrongz, Wrongs wrongs, String name) {
        for (Map.Entry<String, Wrong> et : wrongs.getWrongs().entrySet()) {
            String n = et.getKey(   );
            Wrong  e = et.getValue( );
            wrongz.put(name+"."+n, e);
        }
    }

    public static void failed(Map<String, Wrong> wrongz, Wrong  wrong , String name) {
            wrongz.put(name  , wrong);
    }

}
