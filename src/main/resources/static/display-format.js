(() => {
    const DATE_PATTERN = 'MM/DD/YYYY';
    const PHONE_NUMBER_PATTERN = /^\d{3}-\d{3}-\d{4}$/;
    const moneyFormatter = new Intl.NumberFormat('en-US', {
        style: 'currency',
        currency: 'USD',
        minimumFractionDigits: 0,
        maximumFractionDigits: 0
    });

    const formatDate = value => {
        if (value === null || value === undefined || value === '') return '';
        const text = String(value);
        const isoDate = /^(\d{4})-(\d{2})-(\d{2})/.exec(text);
        if (isoDate) return `${isoDate[2]}/${isoDate[3]}/${isoDate[1]}`;
        const displayDate = /^(\d{1,2})\/(\d{1,2})\/(\d{4})$/.exec(text);
        if (displayDate) {
            return `${displayDate[1].padStart(2, '0')}/${displayDate[2].padStart(2, '0')}/${displayDate[3]}`;
        }
        return text;
    };

    const formatPhone = value => {
        if (value === null || value === undefined || value === '') return '';
        const original = String(value);
        let digits = original.replace(/\D/g, '');
        if (digits.length === 11 && digits.startsWith('1')) digits = digits.slice(1);
        if (digits.length !== 10) return original;
        const formatted = `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`;
        return PHONE_NUMBER_PATTERN.test(formatted) ? formatted : original;
    };

    const formatMoney = value => {
        if (value === null || value === undefined || value === '') return '';
        const amount = Number(value);
        return Number.isFinite(amount) ? moneyFormatter.format(amount) : String(value);
    };

    window.DisplayFormats = Object.freeze({
        DATE_PATTERN,
        PHONE_NUMBER_PATTERN,
        MONEY_PATTERN: '$#,##0',
        formatDate,
        formatPhone,
        formatMoney
    });
})();
