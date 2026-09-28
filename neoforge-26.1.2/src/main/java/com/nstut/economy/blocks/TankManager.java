package com.nstut.economy.blocks;

import com.nstut.economy.api.AccountRef;
import com.nstut.economy.data.EconomyAccountData;
import com.nstut.economy.trading.EconomyFluidStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class TankManager {
    private static final Map<AccountRef,List<EconomyAccountData.VaultRecord>> tanks=new ConcurrentHashMap<>();
    private static EconomyAccountData savedData;

    public static void setAccountData(EconomyAccountData data){
        savedData=data;tanks.clear();
        for(var e:data.getStorageTanks().entrySet())tanks.put(e.getKey(),new CopyOnWriteArrayList<>(e.getValue()));
    }
    public static void register(UUID owner,BlockPos pos,String dim){register(AccountRef.player(owner),pos,dim);}
    public static void register(AccountRef owner,BlockPos pos,String dim){
        if(owner==null)return;
        var list=tanks.computeIfAbsent(owner,k->new CopyOnWriteArrayList<>());
        BlockPos p=pos.immutable();String d=dim!=null?dim:"minecraft:overworld";
        for(var r:list)if(r.pos.equals(p)&&r.dimension.equals(d))return;
        list.add(new EconomyAccountData.VaultRecord(p,d));if(savedData!=null)savedData.addTank(owner,pos,dim);
    }
    public static void register(UUID owner,BlockPos pos){register(AccountRef.player(owner),pos,"minecraft:overworld");}
    public static void register(AccountRef owner,BlockPos pos){register(owner,pos,"minecraft:overworld");}
    public static void unregister(UUID owner,BlockPos pos,String dim){unregister(AccountRef.player(owner),pos,dim);}
    public static void unregister(AccountRef owner,BlockPos pos,String dim){
        if(owner==null)return;var list=tanks.get(owner);if(list==null)return;
        BlockPos p=pos.immutable();String d=dim!=null?dim:"minecraft:overworld";
        list.removeIf(r->r.pos.equals(p)&&r.dimension.equals(d));if(list.isEmpty())tanks.remove(owner);
        if(savedData!=null)savedData.removeTank(owner,pos,dim);
    }
    public static void unregister(BlockPos pos){
        for(var e:new ArrayList<>(tanks.entrySet()))for(var r:new ArrayList<>(e.getValue()))
            if(r.pos.equals(pos))unregister(e.getKey(),r.pos,r.dimension);
    }
    public static void unregister(UUID owner){unregister(AccountRef.player(owner));}
    public static void unregister(AccountRef owner){for(var r:new ArrayList<>(getTankRecords(owner)))unregister(owner,r.pos,r.dimension);}
    public static boolean hasTank(UUID owner){return hasTank(AccountRef.player(owner));}
    public static boolean hasTank(AccountRef owner){var l=tanks.get(owner);return l!=null&&!l.isEmpty();}
    public static List<EconomyAccountData.VaultRecord> getTankRecords(UUID owner){return getTankRecords(AccountRef.player(owner));}
    public static List<EconomyAccountData.VaultRecord> getTankRecords(AccountRef owner){return List.copyOf(tanks.getOrDefault(owner,List.of()));}
    @Nullable public static TankBlockEntity getTank(Level level,UUID owner){return getTank(level,AccountRef.player(owner));}
    @Nullable public static TankBlockEntity getTank(Level level,AccountRef owner){var l=getTanks(level,owner);return l.isEmpty()?null:l.get(0);}
    public static List<TankBlockEntity> getTanks(Level level,UUID owner){return getTanks(level,AccountRef.player(owner));}
    public static List<TankBlockEntity> getTanks(Level level,AccountRef owner){
        var records=tanks.get(owner);if(records==null||records.isEmpty())return Collections.emptyList();
        List<TankBlockEntity> result=new ArrayList<>();
        for(var r:records){Level target=resolveRecordLevel(level,r.dimension);
            if(target==null||!target.dimension().identifier().toString().equals(r.dimension))continue;
            if(target.getBlockEntity(r.pos) instanceof TankBlockEntity tank&&owner.equals(tank.getOwnerRef()))result.add(tank);
        }return result;
    }
    public static void reassignOwner(Level level,AccountRef from,AccountRef to){
        if(from==null||to==null||from.equals(to))return;
        for(var r:new ArrayList<>(getTankRecords(from))){Level target=resolveRecordLevel(level,r.dimension);
            if(target!=null&&target.getBlockEntity(r.pos) instanceof TankBlockEntity tank&&from.equals(tank.getOwnerRef()))tank.setOwner(to);
            else{unregister(from,r.pos,r.dimension);register(to,r.pos,r.dimension);}
        }
    }
    @Nullable private static Level resolveRecordLevel(Level fallback,String dimension){
        if(fallback==null)return null;if(fallback.getServer()!=null)try{
            Identifier rl=com.nstut.economy.compat.Compat.rl(dimension);
            ResourceKey<Level> key=ResourceKey.create(Registries.DIMENSION,rl);return fallback.getServer().getLevel(key);
        }catch(Exception e){com.nstut.Economy.LOGGER.warn("Ignoring storage record with unresolvable dimension {}",dimension);return null;}
        return fallback;
    }
    public static int countFluidInTanks(Level level,UUID owner,Fluid fluid){return countFluidInTanks(level,AccountRef.player(owner),fluid);}
    public static int countFluidInTanks(Level level,AccountRef owner,Fluid fluid){int c=0;for(var t:getTanks(level,owner))if(t.getMode().canSupplyMarket()&&t.getFluid().getFluid()==fluid)c+=t.getFluidAmount();return c;}
    public static int countAvailableFluidSpaceInTanks(Level level,UUID owner,Fluid fluid){return countAvailableFluidSpaceInTanks(level,AccountRef.player(owner),fluid);}
    public static int countAvailableFluidSpaceInTanks(Level level,AccountRef owner,Fluid fluid){
        EconomyFluidStack probe=new EconomyFluidStack(fluid,1);int s=0;for(var t:getTanks(level,owner)){if(!t.getMode().canReceiveMarket())continue;
            if(t.getFluid().isEmpty())s+=t.getCapacity();else if(t.getFluid().isFluidEqual(probe))s+=t.getCapacity()-t.getFluidAmount();}return s;
    }
    public static int extractFluidFromTanks(Level level,UUID owner,Fluid fluid,int amount,List<EconomyFluidStack> dest){return extractFluidFromTanks(level,AccountRef.player(owner),fluid,amount,dest);}
    public static int extractFluidFromTanks(Level level,AccountRef owner,Fluid fluid,int amount,List<EconomyFluidStack> dest){
        if(countFluidInTanks(level,owner,fluid)<amount)return 0;int remaining=amount;
        for(var t:getTanks(level,owner)){if(remaining<=0)break;if(!t.getMode().canSupplyMarket()||t.getFluid().getFluid()!=fluid)continue;
            int take=Math.min(remaining,t.getFluidAmount());var drained=t.drain(take);if(!drained.isEmpty()){dest.add(drained);remaining-=drained.getAmount();}}
        return amount-remaining;
    }
    public static int insertFluidToTanks(Level level,UUID owner,EconomyFluidStack stack){return insertFluidToTanks(level,AccountRef.player(owner),stack);}
    public static int insertFluidToTanks(Level level,AccountRef owner,EconomyFluidStack stack){return insertFluidToTanks(level,owner,stack,true);}
    public static int simulateInsertFluidToTanks(Level level,UUID owner,EconomyFluidStack stack){return simulateInsertFluidToTanks(level,AccountRef.player(owner),stack);}
    public static int simulateInsertFluidToTanks(Level level,AccountRef owner,EconomyFluidStack stack){
        if(stack==null||stack.isEmpty())return 0;int remaining=stack.getAmount();
        for(var t:getTanks(level,owner)){if(remaining<=0)break;if(!t.getMode().canReceiveMarket())continue;var ins=stack.copy();ins.setAmount(remaining);remaining-=t.simulateFill(ins);}
        return stack.getAmount()-remaining;
    }
    public static int restoreFluidToTanks(Level level,UUID owner,EconomyFluidStack stack){return restoreFluidToTanks(level,AccountRef.player(owner),stack);}
    public static int restoreFluidToTanks(Level level,AccountRef owner,EconomyFluidStack stack){return insertFluidToTanks(level,owner,stack,false);}
    private static int insertFluidToTanks(Level level,AccountRef owner,EconomyFluidStack stack,boolean requireOutput){
        int remaining=stack.getAmount();for(var t:getTanks(level,owner)){if(remaining<=0)break;if(requireOutput&&!t.getMode().canReceiveMarket())continue;
            if(!t.getFluid().isEmpty()&&!t.getFluid().isFluidEqual(stack))continue;var ins=stack.copy();ins.setAmount(remaining);remaining-=t.fill(ins);}
        return stack.getAmount()-remaining;
    }
    public static EconomyFluidStack mergeFluids(List<EconomyFluidStack> stacks){EconomyFluidStack m=null;for(var s:stacks){if(s==null||s.isEmpty())continue;if(m==null)m=s.copy();else m.grow(s.getAmount());}return m!=null?m:EconomyFluidStack.EMPTY;}
}
