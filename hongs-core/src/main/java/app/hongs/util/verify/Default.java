package app.hongs.util.verify;

import app.hongs.Core;
import app.hongs.util.Synt;
import app.hongs.action.ActionHelper;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 默认取值
 * <pre>
 * 规则参数:
 *  default-create yes|no 仅创建的时候设置
 *  default-compel yes|no 无论有没有都设置
 * </pre>
 * @author Hongs
 */
public class Default extends Rule {
    @Override
    public Object verify(Object value) {
        boolean crt = Synt.declare(params.get("default-create"), false); // 仅创建的时候设置
        boolean tst = Synt.declare(params.get("default-compel"), false); // 无论有没有都设置

        if (value == null || tst) {
            if (helper.isUpdate() && crt) {
                if (tst) {
                    return BLANK;
                } else {
                    return value;
                }
            }

            value = params.get("default");
            String def = Synt.declare(value , "");

            // 默认时间
            Matcher mat = Pattern.compile("^%now(([+-])(\\d+))?$").matcher(def.trim());
            if (mat.matches()) {
                Date now = new Date();
                if (mat.group(1) != null) {
                    Long msc = Synt.declare(mat.group(2), 0L);
                    if ("+".equals(mat.group(1))) {
                        now.setTime(now.getTime() + msc);
                    } else {
                        now.setTime(now.getTime() - msc);
                    }
                }
                return  now;
            }

            // 应用属性
            if (def.startsWith("%")) {
                return Core.getInstance(ActionHelper.class).getAttribute(def.substring(1));
            }

            // 会话属性
            if (def.startsWith("$")) {
                return Core.getInstance(ActionHelper.class).getSessibute(def.substring(1));
            }
        }
        return  value;
    }
}
