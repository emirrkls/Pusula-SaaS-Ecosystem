#!/usr/bin/env python3
"""
Generate complete 3-month financial seed data with mathematical consistency
"""
import random
from datetime import date, datetime, timedelta
from decimal import Decimal

# Configuration
START_DATE = date(2025, 9, 1)
END_DATE = date(2025, 11, 27)
COMPANY_ID = 1
TECHNICIANS = [2, 3]  # Ali Usta, Veli Usta
CUSTOMERS = list(range(1, 16))  # 15 customers

# Expense tracking from existing script
FIXED_EXPENSES = {
    1: 25000,  # Rent on 1st
    15: 60000  # Salary on 15th
}

def generate_service_tickets():
    """Generate 2-3 tickets per day with realistic data"""
    tickets = []
    ticket_id = 1
   
    current = START_DATE
    while current <= END_DATE:
        # Generate 2-3 tickets per day
        num_tickets = random.randint(2, 3)
       
        for _ in range(num_tickets):
            customer_id = random.choice(CUSTOMERS)
            tech_id = random.choice(TECHNICIANS)
           
            # Determine status based on date
            days_old = (END_DATE - current).days
           
            if days_old > 7:  # Old tickets are completed
                status = 'COMPLETED'
                amount = Decimal(random.randint(500, 5000))
            elif days_old > 3:  # Recent tickets mostly completed
                status = random.choice(['COMPLETED', ''])
                amount = Decimal(random.randint(500, 5000)) if status == 'COMPLETED' else Decimal(0)
            else:  # Very recent = in progress
                status = random.choice(['IN_PROGRESS', 'ASSIGNED', 'PENDING'])
                amount = Decimal(0)
           
            created_at = datetime.combine(current, datetime.min.time()) + timedelta(hours=random.randint(8, 16))
           
            if status == 'COMPLETED':
                updated_at = created_at + timedelta(hours=random.randint(2, 48))
            else:
                updated_at = created_at
           
            scheduled = created_at + timedelta(hours=random.randint(1, 24))
          
            descriptions = [
                'Klima bakım ve temizlik',
                'Kombi arıza kontrolü',
                'Klima gaz dolumu',
                'VRF sistem bakımı',
                'Klima ses problemi',
                'Soğutma performans düşüklüğü',
                'Elektronik kart arızası',
                'Su kaçağı kontrolü',
                'Periyodik bakım',
                'Isıtma problemi'
            ]
           
            notes = [
                'Filtre değişimi yapıldı',
               'Termostat değiştirildi',
                'Gaz eklendi',
                'Genel kontrol yapıldı',
                'Fan motoru değiştirildi',
                'Bakım tamamlandı',
                'Parça sipariş edildi',
                'Kontrol devam ediyor'
            ]
           
            tickets.append({
                'id': ticket_id,
                'company_id': COMPANY_ID,
                'customer_id': customer_id,
                'tech_id': tech_id,
                'status': status,
                'scheduled': scheduled.strftime('%Y-%m-%d %H:%M:%S'),
                'description': random.choice(descriptions),
                'notes': random.choice(notes) if status == 'COMPLETED' else '',
                'amount': amount if status == 'COMPLETED' else Decimal(0),
                'created_at': created_at.strftime('%Y-%m-%d %H:%M:%S'),
                'updated_at': updated_at.strftime('%Y-%m-%d %H:%M:%S')
            })
           
            ticket_id += 1
       
        current += timedelta(days=1)
   
    return tickets

def parse_existing_expenses():
    """Parse expenses from the base SQL script"""
    expenses_by_date = {}
   
    # September expenses (from script)
    sept_expenses = [
        ('2025-09-01', 25000 + 450),
        ('2025-09-02', 320),
        ('2025-09-03', 1250),
        ('2025-09-04', 580),
        ('2025-09-05', 280),
        ('2025-09-06', 1850),
        ('2025-09-08', 420),
        ('2025-09-09', 350),
        ('2025-09-10', 2100),
        ('2025-09-11', 490),
        ('2025-09-15', 60000),
        ('2025-09-16', 860),
        ('2025-09-17', 380),
        ('2025-09-18', 1450),
        ('2025-09-19', 520),
        ('2025-09-20', 310),
        ('2025-09-22', 750),
        ('2025-09-23', 440),
        ('2025-09-24', 290),
        ('2025-09-25', 1920),
        ('2025-09-26', 560),
        ('2025-09-27', 2350),
        ('2025-09-28', 1180),
        ('2025-09-29', 670),
        ('2025-09-30', 420),
    ]
   
    # October expenses
    oct_expenses = [
        ('2025-10-01', 25000 + 510),
        ('2025-10-02', 340),
        ('2025-10-03', 1680),
        ('2025-10-04', 460),
        ('2025-10-07', 395),
        ('2025-10-08', 2200),
        ('2025-10-09', 530),
        ('2025-10-10', 320),
        ('2025-10-11', 1540),
        ('2025-10-15', 60000),
        ('2025-10-16', 480),
        ('2025-10-17', 1890),
        ('2025-10-18', 550),
        ('2025-10-21', 370),
        ('2025-10-22', 1220),
        ('2025-10-23', 490),
        ('2025-10-24', 2150),
        ('2025-10-25', 520),
        ('2025-10-28', 310),
        ('2025-10-29', 2420),
        ('2025-10-30', 1150),
        ('2025-10-31', 690),
    ]
   
    # November expenses
    nov_expenses = [
        ('2025-11-01', 25000 + 540),
        ('2025-11-04', 360),
        ('2025-11-05', 1750),
        ('2025-11-06', 470),
        ('2025-11-07', 2350),
        ('2025-11-08', 510),
        ('2025-11-11', 330),
        ('2025-11-12', 1640),
        ('2025-11-15', 60000),
        ('2025-11-18', 490),
        ('2025-11-19', 1920),
        ('2025-11-20', 530),
        ('2025-11-21', 340),
        ('2025-11-22', 1280),
        ('2025-11-25', 460),
        ('2025-11-26', 310),
    ]
   
    for date_str, amount in sept_expenses + oct_expenses + nov_expenses:
        expenses_by_date[date_str] = Decimal(amount)
   
    return expenses_by_date

def generate_daily_closings(tickets, expenses_by_date):
    """Generate daily closings with correct math"""
    closings = []
   
    # Group tickets by date
    income_by_date = {}
    for ticket in tickets:
        if ticket['status'] == 'COMPLETED' and ticket['amount'] > 0:
            # Use updated_at date for income
            completion_date = ticket['updated_at'].split(' ')[0]
            income_by_date[completion_date] = income_by_date.get(completion_date, Decimal(0)) + ticket['amount']
   
    # Generate closing for each past day
    current = START_DATE
    yesterday = END_DATE - timedelta(days=1)
   
    while current <= yesterday:
        date_str = current.strftime('%Y-%m-%d')
       
        income = income_by_date.get(date_str, Decimal(0))
        expense = expenses_by_date.get(date_str, Decimal(0))
        net_cash = income - expense
       
        closings.append({
            'date': date_str,
            'company_id': COMPANY_ID,
            'income': income,
            'expense': expense,
            'net_cash': net_cash,
            'status': 'CLOSED',
            'closed_at': f'{date_str} 23:59:59',
            'closed_by': 1  # Admin
        })
       
        current += timedelta(days=1)
   
    return closings

def main():
    print("Generating service tickets...")
    tickets = generate_service_tickets()
   
    print("Parsing expenses...")
    expenses = parse_existing_expenses()
   
    print("Calculating daily closings...")
    closings = generate_daily_closings(tickets, expenses)
   
    print("\nGenerating SQL...")
   
    # Generate service tickets SQL
    with open('service_tickets_insert.sql', 'w', encoding='utf-8') as f:
        f.write("-- Service Tickets (Generated)\n\n")
        for t in tickets:
            sql = f"""INSERT INTO service_tickets (id, company_id, customer_id, assigned_technician_id, status, scheduled_date, description, notes, collected_amount, is_deleted, created_at, updated_at)
VALUES ({t['id']}, {t['company_id']}, {t['customer_id']}, {t['tech_id']}, '{t['status']}', '{t['scheduled']}', '{t['description']}', '{t['notes']}', {t['amount']}, false, '{t['created_at']}', '{t['updated_at']}');\n"""
            f.write(sql)
   
    # Generate daily closings SQL
    with open('daily_closings_insert.sql', 'w', encoding='utf-8') as f:
        f.write("-- Daily Closings (Generated)\n\n")
        for c in closings:
            sql = f"""INSERT INTO daily_closings (date, company_id, total_income, total_expense, net_cash, status, closed_at, closed_by_user_id)
VALUES ('{c['date']}', {c['company_id']}, {c['income']}, {c['expense']}, {c['net_cash']}, '{c['status']}', '{c['closed_at']}', {c['closed_by']});\n"""
            f.write(sql)
   
    print(f"\n✓ Generated {len(tickets)} service tickets")
    print(f"✓ Generated {len(closings)} daily closings")
    print("\nFiles created:")
    print("  - service_tickets_insert.sql")
    print("  - daily_closings_insert.sql")
    print("\nMerge these with seed_data_3months.sql")

if __name__ == '__main__':
    main()
