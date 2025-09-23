CREATE OR REPLACE FUNCTION fn_imp_timechk(
    i_currtime TIMESTAMP,
    i_fixed VARCHAR DEFAULT NULL,
    i_interval INTEGER DEFAULT 0,
    i_range VARCHAR DEFAULT NULL,
    i_exit VARCHAR DEFAULT 'Y'
)
RETURNS INTEGER AS $$
DECLARE
    o_return INTEGER;
    v_range1 INTEGER;
    v_range2 INTEGER;
    current_time_int INTEGER;
    start_time TIMESTAMP;
    minutes_diff NUMERIC;
    fixed_time_check INTEGER;
BEGIN
    -- 获取范围开始时间
    SELECT dt_full INTO v_range1
    FROM vw_imp_date
    WHERE dt = COALESCE(
        CAST(
            SUBSTRING(i_range FROM '^[0-9]+') AS INTEGER
        ),
        0
    );

    -- 如果没有找到记录，设置默认值
    IF v_range1 IS NULL THEN
        v_range1 := 0;
    END IF;

    -- 获取范围结束时间
    SELECT dt_full INTO v_range2
    FROM vw_imp_date
    WHERE dt = COALESCE(
        CAST(
            SUBSTRING(i_range FROM '[0-9]+$') AS INTEGER
        ),
        2359
    );

    -- 如果没有找到记录，设置默认值
    IF v_range2 IS NULL THEN
        v_range2 := 2359;
    END IF;

    -- 获取当前时间的HHMM格式
    current_time_int := CAST(TO_CHAR(i_currtime, 'HH24MI') AS INTEGER);

    -- 初始化返回值
    o_return := 0;

    -- 时间间隔任务检查
    IF i_interval > 0 THEN
        -- 计算开始时间
        start_time := DATE_TRUNC('day', i_currtime);

        -- 如果当前时间小于范围开始时间，需要减去一天
        IF current_time_int < v_range1 THEN
            start_time := start_time - INTERVAL '1 day';
        END IF;

        -- 添加开始小时和分钟
        start_time := start_time +
                     INTERVAL '1 hour' * FLOOR(v_range1 / 100) +
                     INTERVAL '1 minute' * (v_range1 % 100);

        -- 计算分钟差
        minutes_diff := EXTRACT(EPOCH FROM (i_currtime - start_time)) / 60;

        -- 检查是否在时间范围内且符合间隔条件
        IF (MOD(ROUND(minutes_diff), i_interval) = 0) AND
           ((v_range1 < v_range2 AND current_time_int BETWEEN v_range1 AND v_range2) OR
            (v_range1 > v_range2 AND (current_time_int >= v_range1 OR current_time_int <= v_range2))) THEN
            o_return := 1;
        END IF;
    END IF;

    -- 定点时间任务检查
    IF o_return = 0 THEN
        -- 检查是否不在0001-0023时间段内
        IF NOT (TO_CHAR(i_currtime, 'HH24MI') BETWEEN '0001' AND '0023') THEN
            -- 处理固定时间检查
            IF i_fixed IS NOT NULL THEN
                -- 格式化当前时间，去掉前导0和末尾00
                DECLARE
                    formatted_time VARCHAR;
                BEGIN
                    formatted_time := TO_CHAR(i_currtime, 'HH24MI');
                    -- 去掉前导0
                    formatted_time := LTRIM(formatted_time, '0');
                    -- 如果结果为空（全是0），设为0
                    IF formatted_time = '' THEN
                        formatted_time := '0';
                    -- 如果以00结尾，去掉00
                    ELSIF RIGHT(formatted_time, 2) = '00' THEN
                        formatted_time := LEFT(formatted_time, LENGTH(formatted_time) - 2);
                        -- 如果去掉00后为空，设为0
                        IF formatted_time = '' THEN
                            formatted_time := '0';
                        END IF;
                    END IF;

                    -- 检查格式化的时间是否在固定时间列表中
                    IF POSITION(',' || formatted_time || ',' IN ',' || i_fixed || ',') > 0 THEN
                        o_return := 1;
                    END IF;
                END;
            ELSE
                -- 如果i_fixed为NULL，使用空格进行检查
                DECLARE
                    formatted_time VARCHAR;
                BEGIN
                    formatted_time := TO_CHAR(i_currtime, 'HH24MI');
                    -- 去掉前导0
                    formatted_time := LTRIM(formatted_time, '0');
                    -- 如果结果为空（全是0），设为0
                    IF formatted_time = '' THEN
                        formatted_time := '0';
                    -- 如果以00结尾，去掉00
                    ELSIF RIGHT(formatted_time, 2) = '00' THEN
                        formatted_time := LEFT(formatted_time, LENGTH(formatted_time) - 2);
                        -- 如果去掉00后为空，设为0
                        IF formatted_time = '' THEN
                            formatted_time := '0';
                        END IF;
                    END IF;

                    -- 检查格式化的时间是否在固定时间列表中（使用空格作为默认值）
                    IF POSITION(',' || formatted_time || ',' IN ', ,') > 0 THEN
                        o_return := 1;
                    END IF;
                END;
            END IF;
        END IF;
    END IF;

    RETURN o_return;

EXCEPTION
    WHEN OTHERS THEN
        -- 如果发生任何错误，返回0
        RETURN 0;
END;
$$ LANGUAGE plpgsql;
