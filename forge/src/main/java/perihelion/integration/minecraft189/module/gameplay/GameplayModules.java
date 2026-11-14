package perihelion.integration.minecraft189.module.gameplay;

import perihelion.core.lifecycle.ClientRuntime;
import perihelion.core.module.ClientModule;

/** Explicit native definitions; no reflection or donor registry. */
public final class GameplayModules {
  private GameplayModules() {}

  public static void register(ClientRuntime runtime) {
    HomeBedTracker homeBeds = new HomeBedTracker(runtime);
    TargetingModules.AntiBot antiBot = new TargetingModules.AntiBot(runtime);
    TargetingModules.Friends friends = new TargetingModules.Friends(runtime);
    AuraModule aura = new AuraModule(runtime, antiBot, friends);
    ProjectileModules.AutoProjectile autoProjectile =
        new ProjectileModules.AutoProjectile(runtime, aura);
    RecoveryModules.AutoPot autoPot = new RecoveryModules.AutoPot(runtime);
    aura.itemRoutine(() -> autoProjectile.busy() || autoPot.busy());
    CombatModules.Velocity velocity = new CombatModules.Velocity(runtime, aura);
    InventoryManagementModules.Manager manager = new InventoryManagementModules.Manager(runtime);
    BuildingModules.Breaker breaker = new BuildingModules.Breaker(runtime, aura, homeBeds);
    aura.buildingRoutine(breaker::blocksAura);
    MovementModules.Speed speed = new MovementModules.Speed(runtime, aura);
    speed.buildingRoutine(breaker::pauseSpeed);
    perihelion.integration.minecraft189.hud.HudModule hud =
        new perihelion.integration.minecraft189.hud.HudModule(runtime, aura::target);
    perihelion.integration.minecraft189.hud.TargetHudModule targetHud =
        new perihelion.integration.minecraft189.hud.TargetHudModule(hud);
    ClientModule[] modules = {
      antiBot,
      friends,
      aura,
      new DisplaceModule(runtime, aura),
      hud,
      targetHud,
      new AimAssistModule(runtime),
      new GhostCombatModules.AutoClicker(runtime),
      new GhostCombatModules.Reach(runtime),
      new GhostCombatModules.HitboxExpand(runtime),
      new GhostCombatModules.KeepSprint(runtime),
      new GhostCombatModules.TriggerBot(runtime),
      new GhostCombatModules.BlockHit(runtime),
      new CameraUtilities.Freecam(runtime),
      new CameraUtilities.Zoom(runtime),
      new BedPlatesModule(runtime),
      new ConvenienceModules.AutoEat(runtime),
      new ConvenienceModules.ChestAura(runtime, aura),
      new CameraModule(runtime),
      new ArmorRatioModule(runtime, aura),
      new HornsModule(runtime),
      new CatEarsModule(runtime),
      new ShaderESPModule(runtime),
      new MinigameAimModule(runtime),
      new ModelVisualModules.FreeLook(runtime),
      new PrivacyModules.AutoF5(runtime, aura),
      new PrivacyModules.NameProtect(runtime),
      new ModelVisualModules.Chams(runtime),
      new ModelVisualModules.Skeletons(runtime),
      new WorldVisualModules.Ambience(runtime),
      new WorldVisualModules.BlockESP(runtime),
      new WorldVisualModules.Breadcrumbs(runtime),
      new WorldVisualModules.Trajectories(runtime),
      new ScreenVisualModules.ESP(runtime),
      new ScreenVisualModules.NameTags(runtime),
      new ScreenVisualModules.TargetESP(runtime, aura),
      new UtilityModules.AutoSwap(runtime, aura, autoProjectile::busy),
      new UtilityModules.Derp(runtime),
      new UtilityModules.InputFixes(runtime),
      new UtilityModules.Disabler(runtime, manager),
      new UtilityModules.AutoChat(runtime),
      new BedWarsUtilsModule(runtime),
      new AutoServerModule(runtime),
      new BasketballModule(runtime),
      new PoolModule(runtime),
      new CombatTimingModules.TickBase(runtime, aura),
      new CombatTimingModules.LagRange(runtime, aura),
      new BackTrackModule(runtime, aura),
      new ProjectileModules.BowAimbot(runtime, aura),
      autoProjectile,
      new CombatModules.Criticals(runtime),
      velocity,
      new JumpResetModule(runtime, velocity),
      new HitSelectModule(runtime, aura),
      new CombatModules.TargetStrafe(runtime, aura),
      new RecoveryModules.AutoApple(runtime),
      autoPot,
      new RecoveryModules.AutoExtinguish(runtime, aura),
      new RecoveryModules.NoFall(runtime),
      new ScaffoldModule(runtime),
      new ClutchModule(runtime),
      new BlockInModule(runtime),
      new BuildingModules.BedDefender(runtime),
      breaker,
      manager,
      new InventoryManagementModules.Stealer(runtime),
      new InventoryModules.FastBreak(runtime),
      new InvMoveModule(runtime),
      new InventoryModules.Refill(runtime),
      new InventoryModules.AutoArmor(runtime),
      new PacketModules.Blink(runtime),
      new PacketModules.FakeLag(runtime),
      new AdditionalMovementModules.NoSlow(runtime),
      new AdditionalMovementModules.Phase(runtime),
      new AdditionalMovementModules.LongJump(runtime),
      new MovementModules.Flight(runtime),
      speed,
      new MovementModules.Step(runtime),
      new MovementModules.WaterWalk(runtime),
      new MovementModules.SafeWalk(runtime),
      new MovementModules.Stasis(runtime),
      new PlayerModules.Regen(runtime),
      new PlayerModules.FastUse(runtime),
      new PlayerModules.AntiVoid(runtime),
      new PlayerModules.Eagle(runtime),
      new PlayerModules.AntiFireball(runtime)
    };
    for (ClientModule module : modules) runtime.modules().register(module);
  }
}
