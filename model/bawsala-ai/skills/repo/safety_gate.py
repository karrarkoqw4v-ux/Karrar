"""أداة مستودع: يمنع أي أمر git خطير (push --force على main، reset --hard) دون تأكيد صريح
كل أداة هنا تصف فقط ما يجب فعله؛ التنفيذ الفعلي لأوامر git يبقى بيد المستخدم أو الوكيل المصرَّح له صراحة.
"""
TOOL_ID = "safety_gate"
DESCRIPTION = "يمنع أي أمر git خطير (push --force على main، reset --hard) دون تأكيد صريح"
