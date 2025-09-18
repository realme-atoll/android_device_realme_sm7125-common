#! /vendor/bin/sh

# ============================================
# init.qcom.post_boot.sh - Ultra-Dynamic Legend Edition v2.1
# Realme 6 Pro (SM7125 - Snapdragon 720G)
# ============================================

MODE="performance"   # options: "performance" or "battery"

# -------------------------
# Enhanced CPU scaling with thermal awareness
# -------------------------
dynamic_cpu_scaling() {
    while true; do
        # Get CPU load and thermal state
        CPU_LOAD=$(awk '{u=$2+$4; t=$2+$4+$5; if(t>0) print int(u/t*100); else print 0}' < /proc/stat | head -n1)
        THERMAL_STATE=$(cat /sys/class/thermal/thermal_zone0/temp 2>/dev/null | head -c 2)
        THERMAL_STATE=${THERMAL_STATE:-50}
        
        # Thermal throttling adjustment
        if [ "$THERMAL_STATE" -gt 80 ]; then
            THERMAL_FACTOR=0.8
        elif [ "$THERMAL_STATE" -gt 70 ]; then
            THERMAL_FACTOR=0.9
        else
            THERMAL_FACTOR=1.0
        fi

        # LITTLE cores (0-5) with thermal awareness
        for cpu in 0 1 2 3 4 5; do
            [ ! -d "/sys/devices/system/cpu/cpu$cpu/cpufreq" ] && continue
            
            if [ "$CPU_LOAD" -lt 15 ]; then
                TARGET_FREQ=300000
            elif [ "$CPU_LOAD" -lt 35 ]; then
                TARGET_FREQ=576000
            elif [ "$CPU_LOAD" -lt 60 ]; then
                TARGET_FREQ=1036800
            else
                TARGET_FREQ=1248000
            fi
            
            # Apply thermal factor
            TARGET_FREQ=$(echo "$TARGET_FREQ * $THERMAL_FACTOR" | bc | cut -d. -f1 2>/dev/null || echo $TARGET_FREQ)
            echo $TARGET_FREQ > /sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_min_freq 2>/dev/null
        done

        # BIG cores (6-7) with thermal awareness
        for cpu in 6 7; do
            [ ! -d "/sys/devices/system/cpu/cpu$cpu/cpufreq" ] && continue
            
            if [ "$CPU_LOAD" -lt 25 ]; then
                TARGET_FREQ=652800
            elif [ "$CPU_LOAD" -lt 55 ]; then
                TARGET_FREQ=1248000
            elif [ "$CPU_LOAD" -lt 80 ]; then
                TARGET_FREQ=1612800
            else
                TARGET_FREQ=1804800
            fi
            
            # Apply thermal factor
            TARGET_FREQ=$(echo "$TARGET_FREQ * $THERMAL_FACTOR" | bc | cut -d. -f1 2>/dev/null || echo $TARGET_FREQ)
            echo $TARGET_FREQ > /sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_min_freq 2>/dev/null
        done

        sleep 3
    done &
}

# -------------------------
# Force enable ZRAM with custom sizes - ALWAYS ENABLED
# -------------------------
force_enable_zram() {
    # Get total RAM in MB
    MemTotalKB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    MemTotalMB=$((MemTotalKB / 1024))
    
    # Determine ZRAM size based on requirements - FORCED ENABLED
    if [ $MemTotalMB -ge 7500 ]; then  # 8GB RAM
        ZRamSizeMB=4096  # 4GB ZRAM
    elif [ $MemTotalMB -ge 5500 ]; then  # 6GB RAM
        ZRamSizeMB=3072  # 3GB ZRAM
    elif [ $MemTotalMB -ge 3500 ]; then  # 4GB RAM
        ZRamSizeMB=2048  # 2GB ZRAM
    else
        ZRamSizeMB=$((MemTotalMB / 2))  # 50% for lower RAM
    fi
    
    ZRamSizeBytes=$((ZRamSizeMB * 1024 * 1024))
    
    # Force stop existing swap
    swapoff /dev/block/zram0 2>/dev/null
    
    # Reset ZRAM device
    echo 1 > /sys/block/zram0/reset 2>/dev/null
    
    # Set compression algorithm with fallbacks
    echo lz4 > /sys/block/zram0/comp_algorithm 2>/dev/null || \
    echo lzo > /sys/block/zram0/comp_algorithm 2>/dev/null || \
    echo lzo-rle > /sys/block/zram0/comp_algorithm 2>/dev/null
    
    # Force configure ZRAM
    echo $ZRamSizeBytes > /sys/block/zram0/disksize
    mkswap /dev/block/zram0 >/dev/null 2>&1
    swapon /dev/block/zram0 -p 32758 2>/dev/null

    # Allow multiple ZRAM threads
    echo 8 > /sys/block/zram0/max_comp_streams 2>/dev/null
    
    # Aggressive memory management for better ZRAM utilization
    if [ $MemTotalMB -ge 7500 ]; then
        echo 80 > /proc/sys/vm/swappiness 2>/dev/null   # 8GB model
    else
        echo 90 > /proc/sys/vm/swappiness 2>/dev/null   # 6GB or lower
    fi

    echo 80 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null # Reduce cache pressure
    echo 0 > /proc/sys/vm/page-cluster 2>/dev/null        # Disable page clustering
}

# -------------------------
# Enhanced CPU Governor & Input Boost
# -------------------------
configure_cpu_governor() {
    # Configure governors for all cores
    for cpu in 0 1 2 3 4 5 6 7; do
        if [ -d "/sys/devices/system/cpu/cpu$cpu/cpufreq" ]; then
            echo schedutil > /sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_governor 2>/dev/null
            
            # Tune schedutil parameters
            if [ -d "/sys/devices/system/cpu/cpu$cpu/cpufreq/schedutil" ]; then
                echo 500  > /sys/devices/system/cpu/cpu$cpu/cpufreq/schedutil/up_rate_limit_us 2>/dev/null
                echo 5000 > /sys/devices/system/cpu/cpu$cpu/cpufreq/schedutil/down_rate_limit_us 2>/dev/null
                echo 1 > /sys/devices/system/cpu/cpu$cpu/cpufreq/schedutil/iowait_boost_enable 2>/dev/null
            fi
        fi
    done

    # Configure input boost based on mode
    if [ -d "/sys/module/cpu_boost/parameters" ]; then
        if [ "$MODE" = "performance" ]; then
            echo "0:1248000 6:1804800" > /sys/module/cpu_boost/parameters/input_boost_freq 2>/dev/null
            echo 300 > /sys/module/cpu_boost/parameters/input_boost_ms 2>/dev/null
            echo 1   > /sys/module/cpu_boost/parameters/sched_boost_on_input 2>/dev/null
        else
            echo "0:960000 6:1248000" > /sys/module/cpu_boost/parameters/input_boost_freq 2>/dev/null
            echo 150 > /sys/module/cpu_boost/parameters/input_boost_ms 2>/dev/null
            echo 0   > /sys/module/cpu_boost/parameters/sched_boost_on_input 2>/dev/null
        fi
    fi
}

# -------------------------
# GPU configuration for Adreno 618
# -------------------------
configure_gpu() {
        # Find GPU devfreq node (best-effort)
    GPU_DEVFREQ=""
    for d in /sys/class/devfreq/*; do
        # look for kgsl or adreno keywords in path or parent device
        if grep -qiE "(kgsl|adreno|gpu|gpubw)" <<< "$(basename "$d") $(readlink -f "$d" 2>/dev/null)"; then
            # check this node actually controls kgsl by looking for 'kgsl' in its consumers or name
            [ -f "$d/devfreq/available_governors" ] && GPU_DEVFREQ="$d" && break
            [ -f "$d/available_governors" ] && GPU_DEVFREQ="$d" && break
        fi
    done

    # fallback: try specific common path
    [ -z "$GPU_DEVFREQ" ] && [ -d /sys/class/kgsl/kgsl-3d0/devfreq ] && GPU_DEVFREQ=/sys/class/kgsl/kgsl-3d0/devfreq

    if [ -z "$GPU_DEVFREQ" ]; then
        # couldn't find node; abort gracefully
        return 0
    fi

    # read available governors
    GOVS=$(cat "$GPU_DEVFREQ/available_governors" 2>/dev/null || true)

    # choose governor preference order
    if echo "$GOVS" | grep -qw "simple_ondemand"; then
        GOV="simple_ondemand"
    elif echo "$GOVS" | grep -qw "msm-adreno-tz"; then
        GOV="msm-adreno-tz"
    elif echo "$GOVS" | grep -qw "performance"; then
        GOV="performance"
    else
        GOV=$(echo "$GOVS" | awk '{print $1}') # pick first available
    fi

    # set governor (guarded)
    if [ -w "$GPU_DEVFREQ/governor" ]; then
        printf '%s' "$GOV" > "$GPU_DEVFREQ/governor" 2>/dev/null || true
    fi

    # get available frequencies (fallback to safe constants)
    AVAIL=$(cat "$GPU_DEVFREQ/available_frequencies" 2>/dev/null || echo "")
    if [ -n "$AVAIL" ]; then
        MINF=$(echo $AVAIL | awk '{print $1}')
        MAXF=$(echo $AVAIL | awk '{print $NF}')
    else
        # safe defaults for Adreno 618 on 720G
        MINF=180000000
        MAXF=750000000
    fi

    # tune frequencies based on MODE
    if [ "$MODE" = "performance" ]; then
        [ -w "$GPU_DEVFREQ/min_freq" ] && printf '%s' "$MINF" > "$GPU_DEVFREQ/min_freq" 2>/dev/null || true
        [ -w "$GPU_DEVFREQ/max_freq" ] && printf '%s' "$MAXF" > "$GPU_DEVFREQ/max_freq" 2>/dev/null || true
        # optional: boost adrenoboost if exposed
        [ -w /sys/class/kgsl/kgsl-3d0/adrenoboost ] && printf '2' > /sys/class/kgsl/kgsl-3d0/adrenoboost 2>/dev/null || true
    else
        # battery mode: cap max a bit lower
        BAT_MAX=$(awk -v mf="$MAXF" 'BEGIN{printf("%d\n", mf*0.87)}') # ~87% of max
        [ -w "$GPU_DEVFREQ/min_freq" ] && printf '180000000' > "$GPU_DEVFREQ/min_freq" 2>/dev/null || true
        [ -w "$GPU_DEVFREQ/max_freq" ] && printf '%s' "$BAT_MAX" > "$GPU_DEVFREQ/max_freq" 2>/dev/null || true
        [ -w /sys/class/kgsl/kgsl-3d0/adrenoboost ] && printf '0' > /sys/class/kgsl/kgsl-3d0/adrenoboost 2>/dev/null || true
    fi

    # optional: tune simple_ondemand params if the governor is active and exposes tunables
    if [ "$GOV" = "simple_ondemand" ]; then
        # find governor params dir (often under the devfreq node or /sys/devices/...)
        for P in "$GPU_DEVFREQ"/*; do
            [ -f "$P/up_threshold" ] && printf '85' > "$P/up_threshold" 2>/dev/null || true
            [ -f "$P/down_latency" ] && printf '200000' > "$P/down_latency" 2>/dev/null || true
        done
    fi

    return 0
}

# -------------------------
# Aggressive RAM Management & Background App Control
# -------------------------
configure_aggressive_ram_management() {
    # LMK (Low Memory Killer) configuration - Keep recent apps alive longer
    if [ -f "/sys/module/lowmemorykiller/parameters/minfree" ]; then
        # More aggressive background killing, but preserve foreground/recent apps
        echo "18432,23040,27648,32256,36864,46080" > /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null
    fi
    
    # Alternative LMK configuration
    if [ -f "/proc/sys/vm/extra_free_kbytes" ]; then
        echo 24576 > /proc/sys/vm/extra_free_kbytes 2>/dev/null
    fi
    
    # PSI (Pressure Stall Information) configuration
    if [ -f "/proc/pressure/memory" ]; then
        echo 70 > /sys/fs/cgroup/memory/memory.pressure_level 2>/dev/null
    fi
    
    # OOM configuration - Don't kill recent apps too quickly
    echo 0 > /proc/sys/vm/oom_kill_allocating_task 2>/dev/null
    echo 0 > /proc/sys/vm/panic_on_oom 2>/dev/null
    
    # Memory reclaim tweaks
    echo 50 > /proc/sys/vm/watermark_scale_factor 2>/dev/null
    echo 1 > /proc/sys/vm/watermark_boost_factor 2>/dev/null
    
    # Background app management via cgroups
    if [ -d "/dev/cpuctl" ]; then
        # Limit background apps CPU usage
        echo 5000 > /dev/cpuctl/background/cpu.shares 2>/dev/null
        echo 95000 > /dev/cpuctl/background/cpu.cfs_quota_us 2>/dev/null
        
        # Be more lenient with foreground apps
        echo 15000 > /dev/cpuctl/foreground/cpu.shares 2>/dev/null
        echo -1 > /dev/cpuctl/foreground/cpu.cfs_quota_us 2>/dev/null
    fi
    
    # Process nice values for better prioritization
    echo 10 > /proc/sys/kernel/sched_child_runs_first 2>/dev/null
    echo 4 > /proc/sys/kernel/sched_autogroup_enabled 2>/dev/null
}

# -------------------------
# Enhanced Scheduler & I/O Tuning
# -------------------------
configure_scheduler_io() {
    # Enhanced SchedTune groups - Better recent app preservation
    if [ -d "/dev/stune" ]; then
        echo 25 > /dev/stune/top-app/schedtune.boost 2>/dev/null
        echo 1  > /dev/stune/top-app/schedtune.prefer_idle 2>/dev/null
        echo 15 > /dev/stune/foreground/schedtune.boost 2>/dev/null
        echo -10 > /dev/stune/background/schedtune.boost 2>/dev/null  # More aggressive background throttling
        echo -15 > /dev/stune/system-background/schedtune.boost 2>/dev/null
        echo 20 > /dev/stune/rt/schedtune.boost 2>/dev/null
    fi

    # Advanced scheduler sysctls
    echo 70 > /proc/sys/kernel/sched_upmigrate 2>/dev/null
    echo 50 > /proc/sys/kernel/sched_downmigrate 2>/dev/null
    echo 90 > /proc/sys/kernel/sched_group_upmigrate 2>/dev/null
    echo 70 > /proc/sys/kernel/sched_group_downmigrate 2>/dev/null
    echo 250000 > /proc/sys/kernel/sched_migration_cost_ns 2>/dev/null
    echo 750000 > /proc/sys/kernel/sched_wakeup_granularity_ns 2>/dev/null
    echo 2000000 > /proc/sys/kernel/sched_latency_ns 2>/dev/null

    # I/O scheduler optimization
    for block in sda sdb sdc dm-0; do
        if [ -e "/sys/block/$block/queue/scheduler" ]; then
            # Try CFQ for better app switching, fallback chain
            if echo cfq > /sys/block/$block/queue/scheduler 2>/dev/null; then
                echo 512 > /sys/block/$block/queue/read_ahead_kb 2>/dev/null
                echo 8 > /sys/block/$block/queue/iosched/quantum 2>/dev/null
                echo 300 > /sys/block/$block/queue/iosched/fifo_expire_sync 2>/dev/null
                echo 1250 > /sys/block/$block/queue/iosched/fifo_expire_async 2>/dev/null
            elif echo bfq > /sys/block/$block/queue/scheduler 2>/dev/null; then
                echo 256 > /sys/block/$block/queue/read_ahead_kb 2>/dev/null
            elif echo deadline > /sys/block/$block/queue/scheduler 2>/dev/null; then
                echo 256 > /sys/block/$block/queue/read_ahead_kb 2>/dev/null
            else
                echo noop > /sys/block/$block/queue/scheduler 2>/dev/null
                echo 128 > /sys/block/$block/queue/read_ahead_kb 2>/dev/null
            fi
            echo 0 > /sys/block/$block/queue/iostats 2>/dev/null
            echo 2 > /sys/block/$block/queue/rq_affinity 2>/dev/null
        fi
    done
}

# -------------------------
# Enhanced Thermal & Network Tuning
# -------------------------
configure_thermal_network() {
    # --- Dynamic Thermal Tuning ---
    for zone in /sys/class/thermal/thermal_zone*; do
        if [ -e "$zone/temp" ]; then
            CURRENT_TEMP=$(cat $zone/temp 2>/dev/null)
            # Set trip points based on current temp
            if [ "$CURRENT_TEMP" -lt 60000 ]; then
                # light usage
                [ -e "$zone/trip_point_0_temp" ] && echo 85000 > $zone/trip_point_0_temp 2>/dev/null
                [ -e "$zone/trip_point_1_temp" ] && echo 95000 > $zone/trip_point_1_temp 2>/dev/null
            else
                # heavy usage
                [ -e "$zone/trip_point_0_temp" ] && echo 90000 > $zone/trip_point_0_temp 2>/dev/null
                [ -e "$zone/trip_point_1_temp" ] && echo 100000 > $zone/trip_point_1_temp 2>/dev/null
            fi
        fi
    done

    # --- Dynamic Network Optimization ---
    # Wi-Fi power save: disable if under high load (gaming/streaming)
    if [ -f "/sys/module/wlan/parameters/iw_power_save_disable" ]; then
        LOAD=$(cat /proc/loadavg | awk '{print $1}')
        if (( $(echo "$LOAD > 1.0" | bc -l) )); then
            echo 0 > /sys/module/wlan/parameters/iw_power_save_disable 2>/dev/null
        else
            echo 1 > /sys/module/wlan/parameters/iw_power_save_disable 2>/dev/null
        fi
    fi

    # TCP tweaks
    echo 1 > /proc/sys/net/ipv4/tcp_low_latency 2>/dev/null
    echo 0 > /proc/sys/net/ipv4/tcp_timestamps 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_sack 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_window_scaling 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_tw_reuse 2>/dev/null
    echo cubic > /proc/sys/net/ipv4/tcp_congestion_control 2>/dev/null

    # Dynamic buffer scaling based on free RAM
    FREE_MEM=$(awk '/MemAvailable/ {print $2}' /proc/meminfo)
    if [ "$FREE_MEM" -ge 4000000 ]; then
        # high free RAM: higher buffers
        echo 262144 > /proc/sys/net/core/rmem_default 2>/dev/null
        echo 262144 > /proc/sys/net/core/wmem_default 2>/dev/null
    else
        # low free RAM: moderate buffers
        echo 131072 > /proc/sys/net/core/rmem_default 2>/dev/null
        echo 131072 > /proc/sys/net/core/wmem_default 2>/dev/null
    fi

    # --- Preserve Idle Sleep ---
    # Do not force CPU/GPU awake here; rely on kernel governors and suspend handling
    # Only tweak thermal/network for performance vs battery

    return 0
}

# -------------------------
# Advanced Memory and VM optimizations
# -------------------------
configure_memory_vm() {
    # VM tunables for better recent app preservation
    echo 40 > /proc/sys/vm/dirty_ratio 2>/dev/null
    echo 8 > /proc/sys/vm/dirty_background_ratio 2>/dev/null
    echo 3000 > /proc/sys/vm/dirty_expire_centisecs 2>/dev/null     # 30s
    echo 500 > /proc/sys/vm/dirty_writeback_centisecs 2>/dev/null   # 5s
    echo 0 > /proc/sys/vm/oom_kill_allocating_task 2>/dev/null
    echo 2 > /proc/sys/vm/overcommit_memory 2>/dev/null  # Allow more overcommit
    echo 95 > /proc/sys/vm/overcommit_ratio 2>/dev/null
    
    # Memory compaction and defragmentation
    echo 1 > /proc/sys/vm/compact_unevictable_allowed 2>/dev/null
    echo never > /sys/kernel/mm/transparent_hugepage/enabled 2>/dev/null
    echo never > /sys/kernel/mm/transparent_hugepage/defrag 2>/dev/null
    
    # NUMA balancing
    echo 0 > /proc/sys/kernel/numa_balancing 2>/dev/null
}

# -------------------------
# Boot Speed & System Optimizations
# -------------------------
configure_bootspeed() {
    echo 0 > /sys/module/printk/parameters/console_suspend 2>/dev/null
    echo N > /sys/module/rcupdate/parameters/rcu_expedited 2>/dev/null
    echo N > /sys/module/rcupdate/parameters/rcu_normal_after_boot 2>/dev/null
    echo "0 0 0 0" > /proc/sys/kernel/printk 2>/dev/null
    
    # Disable unnecessary features
    echo 0 > /proc/sys/kernel/nmi_watchdog 2>/dev/null
    echo 0 > /sys/kernel/debug/tracing/tracing_on 2>/dev/null
    echo 0 > /proc/sys/kernel/hung_task_timeout_secs 2>/dev/null
}

# ============================================
# Main Execution
# ============================================

# Execute configuration functions in optimal order
configure_cpu_governor
configure_gpu  
configure_scheduler_io
force_enable_zram  # ALWAYS ENABLED - NO CONDITIONS
configure_aggressive_ram_management
configure_thermal_network
configure_memory_vm
configure_bootspeed

# Start dynamic scaling (background process)
dynamic_cpu_scaling

# End of Ultra-Dynamic Legend post-boot v2.1