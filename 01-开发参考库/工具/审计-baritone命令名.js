// 校验 BaritoneCommandLongDescMixin 的 41 个 targets 字符串类在 baritone-fabric-26.1.2.jar 中存在
const { execSync } = require('child_process');
const jar = 'd:/mcaddon/26.1.2/02-Baritone自动寻路/baritone-fabric-26.1.2.jar';
const targets = `baritone.command.defaults.AxisCommand
baritone.command.defaults.BlacklistCommand
baritone.command.defaults.BuildCommand
baritone.command.defaults.ClickCommand
baritone.command.defaults.ComeCommand
baritone.command.defaults.CommandAlias
baritone.command.defaults.ETACommand
baritone.command.defaults.ElytraCommand
baritone.command.defaults.ExecutionControlCommands$2
baritone.command.defaults.ExecutionControlCommands$3
baritone.command.defaults.ExecutionControlCommands$4
baritone.command.defaults.ExecutionControlCommands$5
baritone.command.defaults.ExploreCommand
baritone.command.defaults.ExploreFilterCommand
baritone.command.defaults.FarmCommand
baritone.command.defaults.FindCommand
baritone.command.defaults.FollowCommand
baritone.command.defaults.ForceCancelCommand
baritone.command.defaults.GcCommand
baritone.command.defaults.GoalCommand
baritone.command.defaults.GotoCommand
baritone.command.defaults.HelpCommand
baritone.command.defaults.InvertCommand
baritone.command.defaults.LitematicaCommand
baritone.command.defaults.MineCommand
baritone.command.defaults.PathCommand
baritone.command.defaults.PickupCommand
baritone.command.defaults.ProcCommand
baritone.command.defaults.ReloadAllCommand
baritone.command.defaults.RenderCommand
baritone.command.defaults.RepackCommand
baritone.command.defaults.SaveAllCommand
baritone.command.defaults.SelCommand
baritone.command.defaults.SetCommand
baritone.command.defaults.SchematicaCommand
baritone.command.defaults.SurfaceCommand
baritone.command.defaults.ThisWayCommand
baritone.command.defaults.TunnelCommand
baritone.command.defaults.VersionCommand
baritone.command.defaults.WaypointsCommand`.split(/\r?\n/);

let 失败 = 0;
for (const t of targets) {
    try {
        const out = execSync(`javap -cp "${jar}" "${t}"`, { encoding: 'utf8', timeout: 30000 });
        if (out.includes('class') || out.includes('interface')) {
            console.log('✓', t);
        } else { console.log('✗ 异常输出', t); 失败++; }
    } catch (e) {
        console.log('✗ 不存在', t);
        失败++;
    }
}
console.log('\n失败数:', 失败, '/', targets.length);