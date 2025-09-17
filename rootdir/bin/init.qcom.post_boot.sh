#! /vendor/bin/sh

# ============================================
# init.qcom.post_boot.sh - Ultra-Dynamic Legend Edition
# Realme 6 Pro (SM7125 - Snapdragon 720G)
# Features: Dynamic CPU/GPU, adaptive ZRAM, load-aware scaling
# ============================================

MODE="performance"   # options: "performance" or "battery"

# -------------------------
# Dynamic CPU scaling function
# -------------------------
dynamic_cpu_scaling() {
    while true; do
        CPU_LOAD=$(awk '{u=$2+$4; t=$2+$4+$5; if(t>0) print u/t*100; else print 0}' < /proc/stat | head -n1)
        
        # LITTLE cores scaling
        for cpu in 0 1 2 3 4 5; do
            if [ "$CPU_LOAD" -lt 10 ]; then
                echo 300000 > /sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_min_freq
            elif [ "$CPU_LOAD" -lt 30 ]; then
                echo 576000 > /sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_min_freq
            else
                echo 1036800 > /sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_min_freq
            fi
        done

        # BIG cores scaling
        for cpu in 6 7; do
            if [ "$CPU_LOAD" -lt 20 ]; then
                echo 652800 > /sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_min_freq
            elif [ "$CPU_LOAD" -lt 50 ]; then
                echo 1248000 > /sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_min_freq
            else
                echo 1612800 > /sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_min_freq
            fi
        done

        sleep 2
    done &
}

# -------------------------
# Adaptive ZRAM function
# -------------------------
adaptive_zram() {
    # Get total RAM in kB
    MemTotalKB=$(grep MemTotal /proc/meminfo | awk '{print $2}')
    
    # Calculate 50% of RAM in bytes
    ZRamSize=$(( MemTotalKB * 1024 / 2 ))
    
    # Cap at 4GB
    [ $ZRamSize -gt 4294967296 ] && ZRamSize=4294967296
    
    # Apply ZRAM settings
    echo lz4 > /sys/block/zram0/comp_algorithm
    echo 1 > /sys/block/zram0/reset
    echo $ZRamSize > /sys/block/zram0/disksize
    mkswap /dev/block/zram0
    swapon /dev/block/zram0 -p 32758
}

# -------------------------
# CPU Governor & Input Boost
# -------------------------
configure_cpu_governor() {
    for cpu in 0 1 2 3 4 5 6 7; do
        echo schedutil > /sys/devices/system/cpu/cpu$cpu/cpufreq/scaling_governor
        echo 200  > /sys/devices/system/cpu/cpu$cpu/cpufreq/schedutil/up_rate_limit_us
        echo 3000 > /sys/devices/system/cpu/cpu$cpu/cpufreq/schedutil/down_rate_limit_us
    done

    if [ "$MODE" = "performance" ]; then
        echo "0:1248000 6:1804800" > /sys/module/cpu_boost/parameters/input_boost_freq
        echo 200 > /sys/module/cpu_boost/parameters/input_boost_ms
        echo 1   > /sys/module/cpu_boost/parameters/sched_boost_on_input
    else
        echo "0:960000" > /sys/module/cpu_boost/parameters/input_boost_freq
        echo 100 > /sys/module/cpu_boost/parameters/input_boost_ms
        echo 0   > /sys/module/cpu_boost/parameters/sched_boost_on_input
    fi
}

# -------------------------
# GPU Tuning
# -------------------------
configure_gpu() {
    GPUF=/sys/class/kgsl/kgsl-3d0/devfreq
    if [ "$MODE" = "performance" ]; then
        echo msm-adreno-tz > $GPUF/governor
        echo 305000000 > $GPUF/min_freq
        echo 750000000 > $GPUF/max_freq
        echo 3 > /sys/class/kgsl/kgsl-3d0/default_pwrlevel
        echo 1 > /sys/class/kgsl/kgsl-3d0/adrenoboost
    else
        echo msm-adreno-tz > $GPUF/governor
        echo 180000000 > $GPUF/min_freq
        echo 750000000 > $GPUF/max_freq
        echo 5 > /sys/class/kgsl/kgsl-3d0/default_pwrlevel
        echo 0 > /sys/class/kgsl/kgsl-3d0/adrenoboost
    fi
}

# -------------------------
# Scheduler & I/O Tuning
# -------------------------
configure_scheduler_io() {
    # SchedTune groups
    echo 15 > /dev/stune/top-app/schedtune.boost
    echo 1  > /dev/stune/top-app/schedtune.prefer_idle
    echo 5  > /dev/stune/foreground/schedtune.boost
    echo 0  > /dev/stune/background/schedtune.boost
    echo 0  > /dev/stune/system-background/schedtune.boost
    echo 10 > /dev/stune/rt/schedtune.boost

    # Scheduler sysctls
    echo 60 > /proc/sys/kernel/sched_upmigrate
    echo 40 > /proc/sys/kernel/sched_downmigrate
    echo 90 > /proc/sys/kernel/sched_group_upmigrate
    echo 70 > /proc/sys/kernel/sched_group_downmigrate
    echo 500000 > /proc/sys/kernel/sched_migration_cost_ns
    echo 1000000 > /proc/sys/kernel/sched_wakeup_granularity_ns

    # I/O
    echo noop > /sys/block/sda/queue/scheduler
    echo 256 > /sys/block/sda/queue/read_ahead_kb
    echo 0   > /sys/block/sda/queue/iostats
}

# -------------------------
# Thermal & Network Tuning
# -------------------------
configure_thermal_network() {
    for zone in /sys/class/thermal/thermal_zone*; do
        echo 95 > $zone/trip_point_0_temp 2>/dev/null
        echo 105 > $zone/trip_point_1_temp 2>/dev/null
    done
    echo 1 > /sys/class/kgsl/kgsl-3d0/throttling
    echo 0 > /sys/class/kgsl/kgsl-3d0/thermal_pwrlevel

    echo 1 > /sys/module/wlan/parameters/iw_power_save_disable 2>/dev/null
    echo 1 > /proc/sys/net/ipv4/tcp_low_latency
    echo 0 > /proc/sys/net/ipv4/tcp_timestamps
    echo 1 > /proc/sys/net/ipv4/tcp_sack
    echo 1 > /proc/sys/net/ipv4/tcp_window_scaling
    echo 1 > /proc/sys/net/ipv4/tcp_tw_reuse
}

# -------------------------
# Boot Speed
# -------------------------
configure_bootspeed() {
    echo 0 > /sys/module/printk/parameters/console_suspend
    echo N > /sys/module/rcupdate/parameters/rcu_expedited
    echo N > /sys/module/rcupdate/parameters/rcu_normal_after_boot
    echo 0 > /proc/sys/kernel/printk
}

# ============================================
# Main Execution
# ============================================
configure_cpu_governor
configure_gpu
configure_scheduler_io
adaptive_zram
configure_thermal_network
configure_bootspeed

dynamic_cpu_scaling   # starts background dynamic scaling

# End of Ultra-Dynamic Legend post-boot
