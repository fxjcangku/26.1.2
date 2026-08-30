// 附魔交易所 讲台放置服务接口
package com.example.addon.librarian.service;

import com.example.addon.librarian.model.VillagerStation;

public interface LecternPlacementService {
    ActionResult place(VillagerStation station);

    ActionResult breakLectern(VillagerStation station);

    /** 检测讲台位是否有非目标障碍方块 */
    boolean hasObstacle(VillagerStation station);

    /** 挖掉讲台位的障碍方块（自动选最快工具），每tick调用一次直到返回SUCCESS */
    ActionResult breakObstacle(VillagerStation station);

    boolean validatePlacement(VillagerStation station);

    boolean validateRemoval(VillagerStation station);
}
