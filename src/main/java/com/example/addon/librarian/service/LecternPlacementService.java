// 自动图书管理员 讲台放置服务接口
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.VillagerStation;

public interface LecternPlacementService {
    /** 在交易位放置讲台 */
    ActionResult place(VillagerStation station);

    /** 拆除交易位的讲台 */
    ActionResult breakLectern(VillagerStation station);

    /** 检测讲台位是否有非目标障碍方块 */
    boolean hasObstacle(VillagerStation station);

    /** 挖掉讲台位的障碍方块（自动选最快工具），每tick调用一次直到返回SUCCESS */
    ActionResult breakObstacle(VillagerStation station);

    /** 校验讲台是否可放置 */
    boolean validatePlacement(VillagerStation station);

    /** 校验讲台是否可拆除 */
    boolean validateRemoval(VillagerStation station);
}
