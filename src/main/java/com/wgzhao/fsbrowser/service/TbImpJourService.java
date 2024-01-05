package com.wgzhao.fsbrowser.service;

import com.wgzhao.fsbrowser.model.oracle.TbImpJour;
import com.wgzhao.fsbrowser.repository.oracle.TbImpJourRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

@Service
public class TbImpJourService {

    @Autowired
    private TbImpJourRepo tbImpJourRepo;

    /**
     * implement the following sql:
     * select * from (
     * select t.*,
     *         row_number()over(order by updt_date) px,
     *         row_number()over(order by updt_date desc) px2
     * from tb_imp_jour t
     * where updt_date>=to_date('${TD} 1630','YYYYMMDD HH24MI')
     *         and (kind='${kind}' or '${kind}' is null)
     * )
     * where px2<=50
     * order by px desc
     * @param td the trade date in format of YYYYMMDD
     * @return list of {@link TbImpJour}
     */
    public List<TbImpJour> findPipeline(String td) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HH:mm:ss");
        try {
            Date d = sdf.parse(td + " 16:30:00");
            return tbImpJourRepo.findTop50ByUpdtDateAfter(d);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        }
    }
}
