/**
 * 运动记录详情模块
 * 管理运动记录的查看、展示和交互
 */

(function() {
    'use strict';

    // 导出给全局作用域
    window.RecordDetailModule = {
        // 打开运动记录详情
        openDetail: function(record) {
            if (!record) {
                console.error('Record data is required');
                return;
            }

            // 将记录数据保存到 sessionStorage
            sessionStorage.setItem('currentRecord', JSON.stringify(record));

            // 打开详情页面
            window.location.href = 'record-detail.html';
        },

        // 关闭详情页面，返回列表
        closeDetail: function() {
            sessionStorage.removeItem('currentRecord');
            // 固定返回工作页的记录标签
            window.location.href = 'work.html#recordsPanel';
        },

        // 格式化运动记录为可读的格式
        formatRecord: function(record) {
            return {
                recordId: record.recordId || '',
                beginTime: record.beginTime || '',
                endTime: record.endTime || '',
                activeTime: record.activeTime || '',
                distance: parseFloat(record.distance) || 0,
                calorie: parseFloat(record.calorie) || 0,
                avgSpeed: parseFloat(record.avgSpeed) || 0,
                avgPace: record.avgPace || '',
                stepCount: parseInt(record.stepCount) || 0,
                tip: record.tip || '',
                isValid: record.isValid || '-1',
                checkStatus: record.checkStatus || '-2',
                planRouteName: record.planRouteName || '',
                subType: record.subType || ''
            };
        },

        // 获取状态的中文描述
        getStatusText: function(record) {
            if (record.isValid === '1') {
                return '有效';
            } else if (record.checkStatus === '1') {
                return '待审核';
            } else {
                return '无效';
            }
        },

        // 获取状态的样式类
        getStatusClass: function(record) {
            if (record.isValid === '1') {
                return 'valid';
            } else if (record.checkStatus === '1') {
                return 'pending';
            } else {
                return 'invalid';
            }
        },

        // 生成运动记录的摘要文本
        getSummary: function(record) {
            return record.beginTime.substring(5, 16) + ' ~ ' +
                   record.endTime.substring(5, 16) + ' | ' +
                   record.distance + 'km | ' +
                   this.getStatusText(record);
        }
    };

})();
